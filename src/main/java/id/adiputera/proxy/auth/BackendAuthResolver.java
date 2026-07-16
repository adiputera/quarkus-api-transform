package id.adiputera.proxy.auth;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import id.adiputera.proxy.model.AuthConfig;
import id.adiputera.proxy.model.BackendDefinition;
import id.adiputera.proxy.model.BasicAuth;
import id.adiputera.proxy.model.Oauth2Auth;
import id.adiputera.proxy.model.Oauth2Type;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * Resolves the outbound {@code Authorization} header for a backend's configured
 * {@link AuthConfig}.
 *
 * <ul>
 *   <li>No auth → {@link Optional#empty()}: the inbound {@code Authorization}
 *       forwards untouched.</li>
 *   <li>{@link BasicAuth} → {@code Basic base64(username:password)}. Cheap, no
 *       network call, not cached.</li>
 *   <li>{@link Oauth2Auth} → fetches a {@code client_credentials} token from the
 *       authorization server and returns {@code <token_type> <access_token>}.
 *       Tokens are cached in Redis (key {@code proxy:auth:token:{code}}), shared
 *       across proxy instances, with a TTL of the token's lifetime minus a 60s
 *       refresh margin (floored at 5s). A per-instance lock coalesces concurrent
 *       local refreshes; a failed fetch aborts the request and is not cached.</li>
 * </ul>
 *
 * @author Yusuf F. Adiputera
 */
@Slf4j
@ApplicationScoped
public class BackendAuthResolver {

    private static final String KEY_PREFIX = "proxy:auth:token:";
    private static final String DEFAULT_GRANT_TYPE = "client_credentials";
    private static final long REFRESH_MARGIN_SECONDS = 60;
    private static final long MIN_TTL_SECONDS = 5;

    private final RedisDataSource redisDataSource;
    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /**
     * Constructs a new BackendAuthResolver.
     *
     * @param redisDataSource The Quarkus Redis data source.
     */
    @Inject
    public BackendAuthResolver(RedisDataSource redisDataSource) {
        this(redisDataSource, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build(), new ObjectMapper());
    }

    /**
     * Constructs a new BackendAuthResolver with a custom HTTP client and object mapper for testing.
     *
     * @param redisDataSource The Quarkus Redis data source.
     * @param httpClient      The HTTP client used for fetching tokens.
     * @param mapper          The Jackson object mapper used for parsing JSON responses.
     */
    public BackendAuthResolver(RedisDataSource redisDataSource, HttpClient httpClient, ObjectMapper mapper) {
        this.redisDataSource = redisDataSource;
        this.httpClient = httpClient;
        this.mapper = mapper;
    }

    /**
     * Returns the {@code Authorization} header value to send to {@code backend},
     * or {@link Optional#empty()} when the backend has no auth configured.
     *
     * @param backendId backend ID, for log context
     * @param backend   the backend definition (may be {@code null})
     * @return An optional containing the authorization header value if configured.
     * @throws RuntimeException if an OAuth2 token fetch fails or returns no token
     */
    public Optional<String> authorizationHeader(String backendId, BackendDefinition backend) {
        if (backend == null || backend.getAuth() == null) {
            return Optional.empty();
        }
        AuthConfig auth = backend.getAuth();
        if (auth instanceof BasicAuth b) {
            return Optional.of(basicAuthValue(b.username(), b.password()));
        }
        if (auth instanceof Oauth2Auth o) {
            return Optional.of(oauthAuthorization(backendId, o));
        }
        return Optional.empty();
    }

    /**
     * Resolves OAuth2 authorization header value, checking Redis cache or fetching a new token.
     *
     * @param backendId The backend ID.
     * @param o         The OAuth2 auth configuration.
     * @return The authorization header value string.
     */
    private String oauthAuthorization(String backendId, Oauth2Auth o) {
        String key = KEY_PREFIX + o.code();
        ValueCommands<String, String> ops = redisDataSource.value(String.class);
        String cached = ops.get(key);
        if (cached != null) {
            return cached;
        }

        ReentrantLock lock = locks.computeIfAbsent(o.code(), k -> new ReentrantLock());
        lock.lock();
        try {
            cached = ops.get(key);
            if (cached != null) {
                return cached;
            }
            FetchedToken fetched = fetchToken(backendId, o);
            ops.setex(key, fetched.ttlSeconds(), fetched.headerValue());
            return fetched.headerValue();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Fetches a new access token from the configured OAuth2 authorization endpoint.
     *
     * @param backendId The backend ID.
     * @param o         The OAuth2 auth configuration.
     * @return The fetched token record with header value and calculated TTL.
     * @throws RuntimeException if token fetching fails or returned payload is invalid.
     */
    private FetchedToken fetchToken(String backendId, Oauth2Auth o) {
        String grantType = hasText(o.grantType()) ? o.grantType() : DEFAULT_GRANT_TYPE;
        boolean credentialsInHeader = o.oauth2Type() == Oauth2Type.HEADER;

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", grantType);
        if (hasText(o.scope())) {
            form.put("scope", o.scope());
        }
        if (!credentialsInHeader) {
            form.put("client_id", o.clientId());
            form.put("client_secret", o.clientSecret());
        }

        String formString = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                .uri(URI.create(o.url()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formString));

        if (credentialsInHeader) {
            reqBuilder.header("Authorization", basicAuthValue(o.clientId(), o.clientSecret()));
        }

        log.debug("Fetching OAuth2 token for backend '{}' (auth '{}') from {}", backendId, o.code(), o.url());

        HttpResponse<String> resp;
        try {
            resp = httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception ex) {
            log.error("Failed to fetch OAuth2 token for backend '{}' (auth '{}') from {}: {} - {}",
                    backendId, o.code(), o.url(), ex.getClass().getSimpleName(), ex.getMessage());
            if (ex instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException("HTTP send error: " + ex.getMessage(), ex);
        }

        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            log.error("Auth server returned status {} for backend '{}' (auth '{}') from {}: {}",
                    resp.statusCode(), backendId, o.code(), o.url(), resp.body());
            throw new RuntimeException("Auth server returned status " + resp.statusCode());
        }

        TokenResponse tokenResp;
        try {
            tokenResp = mapper.readValue(resp.body(), TokenResponse.class);
        } catch (Exception ex) {
            log.error("Failed to parse OAuth2 token response for backend '{}': {}", backendId, ex.getMessage());
            throw new RuntimeException("Failed to parse token response", ex);
        }

        if (tokenResp == null || !hasText(tokenResp.accessToken())) {
            throw new IllegalStateException(
                    "Auth server returned no access_token for auth '" + o.code() + "'");
        }

        String tokenType = hasText(tokenResp.tokenType()) ? tokenResp.tokenType() : "Bearer";
        long ttl = Math.max(tokenResp.expiresIn() - REFRESH_MARGIN_SECONDS, MIN_TTL_SECONDS);

        log.debug("Obtained token for auth '{}', reported valid {}s, caching in Redis for {}s",
                o.code(), tokenResp.expiresIn(), ttl);
        return new FetchedToken(tokenType + " " + tokenResp.accessToken(), ttl);
    }

    /**
     * Formats username and password into an HTTP Basic authorization header string.
     *
     * @param username The username string.
     * @param password The password string.
     * @return The Basic authorization header value string.
     */
    private static String basicAuthValue(String username, String password) {
        String raw = (username == null ? "" : username) + ":" + (password == null ? "" : password);
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Checks whether the given string is non-null and not blank.
     *
     * @param s The string to check.
     * @return True if non-null and not blank, false otherwise.
     */
    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * A freshly fetched token and how long it should stay cached.
     *
     * @param headerValue The formatted authorization header string.
     * @param ttlSeconds  The cache TTL in seconds.
     */
    private record FetchedToken(String headerValue, long ttlSeconds) {

        /**
         * Gets the formatted authorization header value.
         *
         * @return The header value string.
         */
        @Override
        public String headerValue() {
            return headerValue;
        }

        /**
         * Gets the cache TTL in seconds.
         *
         * @return The TTL in seconds.
         */
        @Override
        public long ttlSeconds() {
            return ttlSeconds;
        }
    }

    /**
     * Subset of the OAuth2 token response consumed; extra fields are ignored.
     *
     * @param accessToken The access token string.
     * @param tokenType   The token type string.
     * @param expiresIn   The reported expiration time in seconds.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record TokenResponse(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("token_type") String tokenType,
            @JsonProperty("expires_in") long expiresIn) {

        /**
         * Gets the access token.
         *
         * @return The access token string.
         */
        @Override
        public String accessToken() {
            return accessToken;
        }

        /**
         * Gets the token type.
         *
         * @return The token type string.
         */
        @Override
        public String tokenType() {
            return tokenType;
        }

        /**
         * Gets the expiration time in seconds.
         *
         * @return The expiration duration in seconds.
         */
        @Override
        public long expiresIn() {
            return expiresIn;
        }
    }
}

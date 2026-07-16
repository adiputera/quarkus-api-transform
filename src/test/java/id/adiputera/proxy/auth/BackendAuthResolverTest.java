package id.adiputera.proxy.auth;

import com.sun.net.httpserver.HttpServer;
import id.adiputera.proxy.model.AuthConfig;
import id.adiputera.proxy.model.BackendDefinition;
import id.adiputera.proxy.model.BasicAuth;
import id.adiputera.proxy.model.Oauth2Auth;
import id.adiputera.proxy.model.Oauth2Type;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.value.ValueCommands;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BackendAuthResolver}.
 *
 * <p>Redis access is mocked; the OAuth2 auth server uses an in-process JDK {@link HttpServer}
 * on an ephemeral port so full HTTP round-trips are verified.</p>
 *
 * @author Yusuf F. Adiputera
 */
class BackendAuthResolverTest {

    private static final String AUTH_CODE = "daytona-oauth2";
    private static final String KEY = "proxy:auth:token:" + AUTH_CODE;

    private HttpServer server;

    /**
     * Stops the in-process HTTP server after each test execution.
     */
    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    /**
     * Starts a stub OAuth2 token server responding with the specified HTTP status and body.
     *
     * @param status   The HTTP status code to return.
     * @param jsonBody The JSON body string to return.
     * @return The auth server stub record containing captured headers and payload.
     * @throws IOException if the HTTP server fails to bind or start.
     */
    private AuthServer startAuthServer(int status, String jsonBody) throws IOException {
        AtomicReference<String> capturedBody = new AtomicReference<>();
        AtomicReference<String> capturedAuthHeader = new AtomicReference<>();
        AtomicInteger hitCount = new AtomicInteger();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/oauth2/token", exchange -> {
            hitCount.incrementAndGet();
            capturedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] req = exchange.getRequestBody().readAllBytes();
            capturedBody.set(new String(req, StandardCharsets.UTF_8));
            byte[] resp = jsonBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/oauth2/token";
        return new AuthServer(url, capturedBody, capturedAuthHeader, hitCount);
    }

    /**
     * Record holding stub auth server state and recorded request attributes.
     *
     * @param url        The bound endpoint URL.
     * @param body       The captured request body.
     * @param authHeader The captured Authorization header.
     * @param hits       The number of requests received.
     */
    private record AuthServer(String url,
                              AtomicReference<String> body,
                              AtomicReference<String> authHeader,
                              AtomicInteger hits) {

        /**
         * Gets the server URL.
         *
         * @return The URL string.
         */
        @Override
        public String url() {
            return url;
        }

        /**
         * Gets the captured body reference.
         *
         * @return The atomic reference for body string.
         */
        @Override
        public AtomicReference<String> body() {
            return body;
        }

        /**
         * Gets the captured auth header reference.
         *
         * @return The atomic reference for header string.
         */
        @Override
        public AtomicReference<String> authHeader() {
            return authHeader;
        }

        /**
         * Gets the hit count reference.
         *
         * @return The atomic integer of hits.
         */
        @Override
        public AtomicInteger hits() {
            return hits;
        }
    }

    /**
     * Creates a dummy backend definition configured with the given auth.
     *
     * @param auth The auth configuration object.
     * @return The initialized backend definition.
     */
    private static BackendDefinition backendWith(AuthConfig auth) {
        BackendDefinition b = new BackendDefinition();
        b.setBaseUrl("https://backend.example.com");
        b.setAuth(auth);
        return b;
    }

    /**
     * Creates an OAuth2 auth configuration instance for testing.
     *
     * @param tokenUrl The token endpoint URL.
     * @param type     The OAuth2 credential placement type.
     * @return The initialized OAuth2 configuration object.
     */
    private static Oauth2Auth oauth2(String tokenUrl, Oauth2Type type) {
        return new Oauth2Auth(AUTH_CODE, tokenUrl, "astraDaihatsu_mobileApp_client",
                "secret-123", "openid", "client_credentials", type);
    }

    /**
     * Stubs the Quarkus RedisDataSource value operations for testing.
     *
     * @param redis The mock RedisDataSource.
     * @return The mock ValueCommands object.
     */
    @SuppressWarnings("unchecked")
    private static ValueCommands<String, String> stubValueOps(RedisDataSource redis) {
        ValueCommands<String, String> ops = mock(ValueCommands.class);
        when(redis.value(String.class)).thenReturn(ops);
        return ops;
    }

    @Test
    void noAuthReturnsEmptyAndNeverTouchesRedis() {
        RedisDataSource redis = mock(RedisDataSource.class);
        BackendAuthResolver resolver = new BackendAuthResolver(redis);

        assertThat(resolver.authorizationHeader("plain-api", backendWith(null))).isEmpty();
        verifyNoInteractions(redis);
    }

    @Test
    void nullBackendReturnsEmpty() {
        RedisDataSource redis = mock(RedisDataSource.class);
        BackendAuthResolver resolver = new BackendAuthResolver(redis);

        assertThat(resolver.authorizationHeader("whatever", null)).isEmpty();
        verifyNoInteractions(redis);
    }

    @Test
    void basicAuthAppendsBasicHeaderWithoutRedisOrHttp() {
        RedisDataSource redis = mock(RedisDataSource.class);
        BackendAuthResolver resolver = new BackendAuthResolver(redis);

        Optional<String> result = resolver.authorizationHeader("basic-api",
                backendWith(new BasicAuth("basic-code", "user1", "pass1")));

        String expected = "Basic " + Base64.getEncoder()
                .encodeToString("user1:pass1".getBytes(StandardCharsets.UTF_8));
        assertThat(result).contains(expected);
        verifyNoInteractions(redis);
    }

    @Test
    void oauthCacheHitReturnsCachedTokenWithoutCallingAuthServer() throws IOException {
        AuthServer auth = startAuthServer(200, "{\"access_token\":\"fresh\",\"expires_in\":1799}");
        RedisDataSource redis = mock(RedisDataSource.class);
        ValueCommands<String, String> ops = stubValueOps(redis);
        when(ops.get(KEY)).thenReturn("Bearer cached-token");

        BackendAuthResolver resolver = new BackendAuthResolver(redis);
        Optional<String> result = resolver.authorizationHeader("daytona-api",
                backendWith(oauth2(auth.url(), Oauth2Type.BODY)));

        assertThat(result).contains("Bearer cached-token");
        assertThat(auth.hits()).hasValue(0);
        verify(ops, never()).setex(anyString(), anyLong(), anyString());
    }

    @Test
    void oauthBodyCacheMissFetchesWithCredentialsInFormStoresWithTtl() throws IOException {
        AuthServer auth = startAuthServer(200,
                "{\"access_token\":\"tok-xyz\",\"token_type\":\"Bearer\",\"expires_in\":1799}");
        RedisDataSource redis = mock(RedisDataSource.class);
        ValueCommands<String, String> ops = stubValueOps(redis);
        when(ops.get(KEY)).thenReturn(null);

        BackendAuthResolver resolver = new BackendAuthResolver(redis);
        Optional<String> result = resolver.authorizationHeader("daytona-api",
                backendWith(oauth2(auth.url(), Oauth2Type.BODY)));

        assertThat(result).contains("Bearer tok-xyz");
        assertThat(auth.hits()).hasValue(1);
        assertThat(auth.authHeader().get()).isNull();
        assertThat(auth.body().get())
                .contains("grant_type=client_credentials")
                .contains("scope=openid")
                .contains("client_id=astraDaihatsu_mobileApp_client")
                .contains("client_secret=secret-123");
        verify(ops).setex(KEY, 1739L, "Bearer tok-xyz");
    }

    @Test
    void oauthHeaderSendsBasicHeaderAndOmitsCredentialsFromBody() throws IOException {
        AuthServer auth = startAuthServer(200, "{\"access_token\":\"tok-h\",\"expires_in\":1799}");
        RedisDataSource redis = mock(RedisDataSource.class);
        stubValueOps(redis);

        BackendAuthResolver resolver = new BackendAuthResolver(redis);
        Optional<String> result = resolver.authorizationHeader("daytona-api",
                backendWith(oauth2(auth.url(), Oauth2Type.HEADER)));

        assertThat(result).contains("Bearer tok-h");
        String expectedBasic = "Basic " + Base64.getEncoder()
                .encodeToString("astraDaihatsu_mobileApp_client:secret-123".getBytes(StandardCharsets.UTF_8));
        assertThat(auth.authHeader().get()).isEqualTo(expectedBasic);
        assertThat(auth.body().get())
                .contains("grant_type=client_credentials")
                .contains("scope=openid")
                .doesNotContain("client_id=")
                .doesNotContain("client_secret=");
    }

    @Test
    void defaultsTokenTypeToBearerWhenResponseOmitsIt() throws IOException {
        AuthServer auth = startAuthServer(200, "{\"access_token\":\"no-type\",\"expires_in\":1799}");
        RedisDataSource redis = mock(RedisDataSource.class);
        stubValueOps(redis);

        BackendAuthResolver resolver = new BackendAuthResolver(redis);
        Optional<String> result = resolver.authorizationHeader("daytona-api",
                backendWith(oauth2(auth.url(), Oauth2Type.BODY)));

        assertThat(result).contains("Bearer no-type");
    }

    @Test
    void shortLivedTokenIsCachedForAtLeastTheMinimumTtl() throws IOException {
        AuthServer auth = startAuthServer(200, "{\"access_token\":\"brief\",\"expires_in\":10}");
        RedisDataSource redis = mock(RedisDataSource.class);
        ValueCommands<String, String> ops = stubValueOps(redis);

        BackendAuthResolver resolver = new BackendAuthResolver(redis);
        resolver.authorizationHeader("daytona-api", backendWith(oauth2(auth.url(), Oauth2Type.BODY)));

        verify(ops).setex(KEY, 5L, "Bearer brief");
    }

    @Test
    void missingAccessTokenInResponseThrows() throws IOException {
        AuthServer auth = startAuthServer(200, "{\"scope\":\"openid\",\"expires_in\":1799}");
        RedisDataSource redis = mock(RedisDataSource.class);
        stubValueOps(redis);

        BackendAuthResolver resolver = new BackendAuthResolver(redis);

        assertThatThrownBy(() -> resolver.authorizationHeader("daytona-api",
                backendWith(oauth2(auth.url(), Oauth2Type.BODY))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no access_token");
    }

    @Test
    void authServerErrorPropagatesAndDoesNotPoisonCache() throws IOException {
        AuthServer auth = startAuthServer(401, "{\"error\":\"invalid_client\"}");
        RedisDataSource redis = mock(RedisDataSource.class);
        ValueCommands<String, String> ops = stubValueOps(redis);

        BackendAuthResolver resolver = new BackendAuthResolver(redis);

        assertThatThrownBy(() -> resolver.authorizationHeader("daytona-api",
                backendWith(oauth2(auth.url(), Oauth2Type.BODY))))
                .isInstanceOf(RuntimeException.class);
        verify(ops, never()).setex(anyString(), anyLong(), anyString());
    }
}

package id.adiputera.proxy.proxy;

import id.adiputera.proxy.auth.BackendAuthResolver;
import id.adiputera.proxy.config.ConfigProvider;
import id.adiputera.proxy.config.ConfigSnapshot;
import id.adiputera.proxy.exception.ErrorResponse;
import id.adiputera.proxy.model.BackendDefinition;
import id.adiputera.proxy.model.RouteDefinition;
import id.adiputera.proxy.routing.RouteMatcher;
import id.adiputera.proxy.transform.TransformOrchestrator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Core orchestrator handling the full proxy lifecycle for each incoming request.
 *
 * <p>Matches routes, executes transformations, injects backend authentication,
 * forwards requests using Java {@link HttpClient}, and rewrites response headers.</p>
 *
 * @author Yusuf F. Adiputera
 */
@Slf4j
@ApplicationScoped
public class ProxyService {

    private static final Set<String> DISALLOWED_REQUEST_HEADERS = Set.of(
            "connection", "content-length", "expect", "host", "upgrade");

    private static final Set<String> DISALLOWED_RESPONSE_HEADERS = Set.of(
            "connection", "content-length", "transfer-encoding", "keep-alive",
            "proxy-authenticate", "proxy-authorization", "te", "trailers", "upgrade", "content-encoding");

    private final RouteMatcher routeMatcher;
    private final TransformOrchestrator transformOrchestrator;
    private final ConfigProvider configProvider;
    private final ResponseHeaderRewriter responseHeaderRewriter;
    private final BackendAuthResolver backendAuthResolver;

    /**
     * Constructs a new ProxyService.
     *
     * @param routeMatcher           The route matching engine.
     * @param transformOrchestrator  The parameter transformation pipeline.
     * @param configProvider         The active configuration provider.
     * @param responseHeaderRewriter The response header rewriter for Location headers.
     * @param backendAuthResolver    The backend authentication resolver.
     */
    public ProxyService(RouteMatcher routeMatcher,
                        TransformOrchestrator transformOrchestrator,
                        ConfigProvider configProvider,
                        ResponseHeaderRewriter responseHeaderRewriter,
                        BackendAuthResolver backendAuthResolver) {
        this.routeMatcher = routeMatcher;
        this.transformOrchestrator = transformOrchestrator;
        this.configProvider = configProvider;
        this.responseHeaderRewriter = responseHeaderRewriter;
        this.backendAuthResolver = backendAuthResolver;
    }

    /**
     * Proxies an inbound JAX-RS request to the target upstream backend.
     *
     * @param method  The HTTP method string.
     * @param uriInfo The URI info containing request path and query parameters.
     * @param headers The inbound request headers.
     * @param body    The raw request body byte array.
     * @return The upstream response, or 404/error response on route failure.
     * @throws IOException          if network send fails.
     * @throws InterruptedException if the request thread is interrupted.
     */
    public Response proxy(String method, UriInfo uriInfo, HttpHeaders headers, byte[] body)
            throws IOException, InterruptedException {

        ConfigSnapshot snapshot = configProvider.current();

        String path = uriInfo.getRequestUri().getPath();
        MultivaluedMap<String, String> queryParams = uriInfo.getQueryParameters();
        Set<String> presentParams = new HashSet<>(queryParams.keySet());

        RouteMatcher.MatchResult matchResult = routeMatcher.match(method, path, presentParams);
        if (matchResult == null) {
            log.warn("No route matched for {} {}", method, path);
            return Response.status(404)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(new ErrorResponse("RouteNotFoundError",
                            "No route matched for " + method + " " + path))
                    .build();
        }

        RouteDefinition route = matchResult.route();
        log.info("Route matched: {} for {} {}", route.getId(), method, path);

        BackendDefinition backend = snapshot.backends().get(route.getBackend());
        if (backend == null) {
            log.error("Backend '{}' not found in configuration for route '{}'", route.getBackend(), route.getId());
            throw new IllegalStateException("Backend '" + route.getBackend() + "' not found in configuration");
        }

        byte[] requestBody = body == null ? new byte[0] : body;
        MediaType contentType = headers.getMediaType();

        Map<String, String[]> rawQueryParams = toArrayMap(queryParams);

        Map<String, List<String>> inboundHeaders = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.getRequestHeaders().entrySet()) {
            if (entry.getKey() != null) {
                inboundHeaders.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        TransformOrchestrator.Result tr = transformOrchestrator.apply(
                route,
                matchResult.pathVariables(),
                rawQueryParams,
                requestBody,
                contentType,
                inboundHeaders,
                backend.getBaseUrl());

        log.debug("Forwarding to: {}", tr.targetUri());

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(tr.targetUri());

        Set<String> stripSet = new HashSet<>();
        for (String name : snapshot.stripHeaders()) {
            if (name != null) {
                stripSet.add(name.toLowerCase(Locale.ROOT));
            }
        }

        Map<String, List<String>> forwardHeaders = new LinkedHashMap<>();
        if (tr.forwardHeaders() != null) {
            for (Map.Entry<String, List<String>> entry : tr.forwardHeaders().entrySet()) {
                if (entry.getKey() == null) continue;
                String lower = entry.getKey().toLowerCase(Locale.ROOT);
                if (stripSet.contains(lower) || DISALLOWED_REQUEST_HEADERS.contains(lower)) {
                    continue;
                }
                forwardHeaders.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
        }

        if (tr.forwardContentType() != null) {
            putSingleHeader(forwardHeaders, "Content-Type", tr.forwardContentType().toString());
        }

        backendAuthResolver.authorizationHeader(route.getBackend(), backend)
                .ifPresent(value -> putSingleHeader(forwardHeaders, "Authorization", value));

        for (Map.Entry<String, List<String>> entry : forwardHeaders.entrySet()) {
            for (String value : entry.getValue()) {
                try {
                    reqBuilder.header(entry.getKey(), value);
                } catch (IllegalArgumentException ignored) {
                    // Java HttpClient rejects restricted headers; drop silently.
                }
            }
        }

        HttpRequest.BodyPublisher publisher = hasBody(method) && tr.forwardBody() != null && tr.forwardBody().length > 0
                ? HttpRequest.BodyPublishers.ofByteArray(tr.forwardBody())
                : HttpRequest.BodyPublishers.noBody();
        reqBuilder.method(method.toUpperCase(Locale.ROOT), publisher);

        Integer readTimeoutMs = snapshot.backendReadTimeoutsMs().get(route.getBackend());
        if (readTimeoutMs != null && readTimeoutMs > 0) {
            reqBuilder.timeout(Duration.ofMillis(readTimeoutMs));
        }

        HttpClient client = snapshot.backendHttpClients().get(route.getBackend());
        if (client == null) {
            throw new IllegalStateException("HttpClient not found for backend '" + route.getBackend() + "'");
        }

        HttpResponse<byte[]> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());

        Map<String, List<String>> responseHeaders = stripResponseHopByHop(response.headers().map());
        responseHeaders = responseHeaderRewriter.rewriteLocation(responseHeaders, snapshot);

        Response.ResponseBuilder out = Response.status(response.statusCode());
        for (Map.Entry<String, List<String>> h : responseHeaders.entrySet()) {
            String name = h.getKey();
            if (name == null || name.startsWith(":")) {
                continue;
            }
            for (String value : h.getValue()) {
                out.header(name, value);
            }
        }

        byte[] responseBody = response.body();
        if (responseBody != null && responseBody.length > 0) {
            out.entity(responseBody);
        }
        return out.build();
    }

    /**
     * Replaces or sets a single header value in the given headers map case-insensitively.
     *
     * @param map   The headers map.
     * @param name  The header name.
     * @param value The value string.
     */
    private static void putSingleHeader(Map<String, List<String>> map, String name, String value) {
        String existingKey = null;
        for (String k : map.keySet()) {
            if (k != null && k.equalsIgnoreCase(name)) {
                existingKey = k;
                break;
            }
        }
        if (existingKey != null) {
            map.remove(existingKey);
        }
        map.put(name, new ArrayList<>(List.of(value)));
    }

    /**
     * Strips response hop-by-hop headers from the response headers map.
     *
     * @param source The source map of response headers.
     * @return A filtered map without hop-by-hop headers.
     */
    private static Map<String, List<String>> stripResponseHopByHop(Map<String, List<String>> source) {
        Map<String, List<String>> filtered = new LinkedHashMap<>();
        if (source == null) {
            return filtered;
        }
        for (Map.Entry<String, List<String>> entry : source.entrySet()) {
            String name = entry.getKey();
            if (name == null || name.startsWith(":")) {
                continue;
            }
            if (!DISALLOWED_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                filtered.put(name, new ArrayList<>(entry.getValue()));
            }
        }
        return filtered;
    }

    /**
     * Checks if the given HTTP method typically supports a request body.
     *
     * @param method The HTTP method name.
     * @return True if body is supported, false otherwise.
     */
    private static boolean hasBody(String method) {
        String m = method.toUpperCase(Locale.ROOT);
        return !("GET".equals(m) || "HEAD".equals(m) || "DELETE".equals(m));
    }

    /**
     * Converts JAX-RS MultivaluedMap to standard String array map for query parameters.
     *
     * @param src The JAX-RS query parameters map.
     * @return A standard map of string arrays.
     */
    private static Map<String, String[]> toArrayMap(MultivaluedMap<String, String> src) {
        Map<String, String[]> out = new LinkedHashMap<>();
        if (src == null) {
            return out;
        }
        for (Map.Entry<String, List<String>> e : src.entrySet()) {
            out.put(e.getKey(), e.getValue().toArray(new String[0]));
        }
        return out;
    }
}

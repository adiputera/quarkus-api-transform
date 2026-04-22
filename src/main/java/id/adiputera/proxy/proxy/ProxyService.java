package id.adiputera.proxy.proxy;

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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Slf4j
@ApplicationScoped
public class ProxyService {

    private static final Set<String> DISALLOWED_REQUEST_HEADERS = Set.of(
            "connection", "content-length", "expect", "host", "upgrade");

    private static final Set<String> DISALLOWED_RESPONSE_HEADERS = Set.of(
            "connection", "content-length", "transfer-encoding");

    private final RouteMatcher routeMatcher;
    private final TransformOrchestrator transformOrchestrator;
    private final ConfigProvider configProvider;

    public ProxyService(RouteMatcher routeMatcher,
                        TransformOrchestrator transformOrchestrator,
                        ConfigProvider configProvider) {
        this.routeMatcher = routeMatcher;
        this.transformOrchestrator = transformOrchestrator;
        this.configProvider = configProvider;
    }

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
        byte[] requestBody = body == null ? new byte[0] : body;
        MediaType contentType = headers.getMediaType();

        Map<String, String[]> rawQueryParams = toArrayMap(queryParams);

        TransformOrchestrator.Result tr = transformOrchestrator.apply(
                route,
                matchResult.pathVariables(),
                rawQueryParams,
                requestBody,
                contentType,
                backend.getBaseUrl());

        log.debug("Forwarding to: {}", tr.targetUri());

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(tr.targetUri());

        MultivaluedMap<String, String> inboundHeaders = headers.getRequestHeaders();
        Set<String> stripSet = new HashSet<>();
        for (String name : snapshot.stripHeaders()) {
            stripSet.add(name.toLowerCase(Locale.ROOT));
        }
        for (Map.Entry<String, List<String>> entry : inboundHeaders.entrySet()) {
            String lower = entry.getKey().toLowerCase(Locale.ROOT);
            if (stripSet.contains(lower) || DISALLOWED_REQUEST_HEADERS.contains(lower)) {
                continue;
            }
            if (tr.forwardContentType() != null && "content-type".equals(lower)) {
                continue;
            }
            for (String value : entry.getValue()) {
                try {
                    reqBuilder.header(entry.getKey(), value);
                } catch (IllegalArgumentException ignored) {
                    // Java HttpClient rejects some headers; drop those silently.
                }
            }
        }
        if (tr.forwardContentType() != null) {
            reqBuilder.header("Content-Type", tr.forwardContentType().toString());
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
        HttpResponse<byte[]> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());

        Response.ResponseBuilder out = Response.status(response.statusCode());
        for (Map.Entry<String, List<String>> h : response.headers().map().entrySet()) {
            String name = h.getKey();
            if (name.startsWith(":")) {
                continue;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            if (DISALLOWED_RESPONSE_HEADERS.contains(lower)) {
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

    private static boolean hasBody(String method) {
        String m = method.toUpperCase(Locale.ROOT);
        return !("GET".equals(m) || "HEAD".equals(m) || "DELETE".equals(m));
    }

    private static Map<String, String[]> toArrayMap(MultivaluedMap<String, String> src) {
        Map<String, String[]> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : src.entrySet()) {
            out.put(e.getKey(), e.getValue().toArray(new String[0]));
        }
        return out;
    }
}

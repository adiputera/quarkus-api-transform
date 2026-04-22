package id.adiputera.proxy.proxy;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import id.adiputera.proxy.config.ConfigProvider;
import id.adiputera.proxy.config.ConfigSnapshot;
import id.adiputera.proxy.model.BackendDefinition;
import id.adiputera.proxy.model.ParamTransform;
import id.adiputera.proxy.model.RouteDefinition;
import id.adiputera.proxy.routing.RouteCompiler;
import id.adiputera.proxy.routing.RouteMatcher;
import id.adiputera.proxy.transform.RequestTransformer;
import id.adiputera.proxy.transform.TransformOrchestrator;
import id.adiputera.proxy.transform.body.BodyCodecRegistry;
import id.adiputera.proxy.transform.body.FormUrlEncodedBodyCodec;
import id.adiputera.proxy.transform.body.JsonBodyCodec;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProxyServiceTest {

    private WireMockServer wireMock;

    @BeforeEach
    void startWireMock() {
        wireMock = new WireMockServer(options().dynamicPort());
        wireMock.start();
    }

    @AfterEach
    void stopWireMock() {
        wireMock.stop();
    }

    private ProxyService proxyServiceFor(RouteDefinition... routes) {
        BackendDefinition backend = new BackendDefinition();
        backend.setBaseUrl(wireMock.baseUrl());
        for (RouteDefinition r : routes) {
            r.setBackend("test-api");
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        ConfigSnapshot snapshot = new ConfigSnapshot(
                RouteCompiler.compile(List.of(routes)),
                Map.of("test-api", backend),
                Map.of("test-api", client),
                Map.of("test-api", 30000),
                List.of("Authorization", "Content-Type", "Accept", "X-Request-Id", "X-Correlation-Id"),
                List.of("Host", "Connection", "Content-Length"),
                5000, 30000);

        ConfigProvider provider = Mockito.mock(ConfigProvider.class);
        Mockito.when(provider.current()).thenReturn(snapshot);

        RouteMatcher matcher = new RouteMatcher(provider);
        BodyCodecRegistry codecs = new BodyCodecRegistry(
                List.of(new JsonBodyCodec(), new FormUrlEncodedBodyCodec()));
        TransformOrchestrator orchestrator = new TransformOrchestrator(codecs, new RequestTransformer());

        return new ProxyService(matcher, orchestrator, provider);
    }

    private static RouteDefinition route(String id, String source, String target, List<String> methods,
                                         ParamTransform... transforms) {
        RouteDefinition r = new RouteDefinition();
        r.setId(id);
        r.setSource(source);
        r.setTarget(target);
        r.setMethods(methods);
        r.setTransforms(new ArrayList<>(Arrays.asList(transforms)));
        return r;
    }

    private static RouteDefinition route(String id, String source, String target, List<String> methods,
                                         String produces, ParamTransform... transforms) {
        RouteDefinition r = route(id, source, target, methods, transforms);
        r.setProduces(produces);
        return r;
    }

    private static ParamTransform tx(String from, String to) {
        ParamTransform t = new ParamTransform();
        t.setFrom(from);
        t.setTo(to);
        return t;
    }

    private static ParamTransform txDrop(String from) {
        ParamTransform t = new ParamTransform();
        t.setFrom(from);
        return t;
    }

    private static UriInfo uriInfo(String path, Map<String, String> queryParams) {
        StringBuilder query = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, String> e : queryParams.entrySet()) {
            query.append(first ? "?" : "&");
            first = false;
            query.append(e.getKey()).append("=").append(e.getValue());
        }
        URI uri = URI.create("http://localhost:8080" + path + query);

        MultivaluedMap<String, String> qp = new MultivaluedHashMap<>();
        for (Map.Entry<String, String> e : queryParams.entrySet()) {
            qp.add(e.getKey(), e.getValue());
        }

        UriInfo info = Mockito.mock(UriInfo.class);
        Mockito.when(info.getRequestUri()).thenReturn(uri);
        Mockito.when(info.getQueryParameters()).thenReturn(qp);
        return info;
    }

    private static HttpHeaders headers(String contentType, Map<String, String> extras) {
        MultivaluedMap<String, String> hm = new MultivaluedHashMap<>();
        if (contentType != null) {
            hm.add("Content-Type", contentType);
        }
        for (Map.Entry<String, String> e : extras.entrySet()) {
            hm.add(e.getKey(), e.getValue());
        }

        HttpHeaders headers = Mockito.mock(HttpHeaders.class);
        Mockito.when(headers.getRequestHeaders()).thenReturn(hm);
        Mockito.when(headers.getMediaType()).thenReturn(
                contentType == null ? null : MediaType.valueOf(contentType));
        return headers;
    }

    @Test
    void forwardsAllHeadersExceptStripped() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/test", "/api/test", List.of("GET")));
        wireMock.stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withStatus(200)));

        svc.proxy("GET", uriInfo("/test", Map.of()),
                headers(null, Map.of(
                        "Authorization", "Bearer token123",
                        "X-Custom-Header", "custom-value",
                        "Host", "old-api.example.com",
                        "Connection", "keep-alive")),
                null);

        wireMock.verify(anyRequestedFor(urlEqualTo("/api/test"))
                .withHeader("Authorization", equalTo("Bearer token123"))
                .withHeader("X-Custom-Header", equalTo("custom-value")));
    }

    @Test
    void strippedHeadersAreNotForwarded() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/test", "/api/test", List.of("GET")));
        wireMock.stubFor(get(urlEqualTo("/api/test")).willReturn(aResponse().withStatus(200)));

        svc.proxy("GET", uriInfo("/test", Map.of()),
                headers(null, Map.of(
                        "Authorization", "Bearer abc",
                        "Host", "old-api.example.com",
                        "Connection", "keep-alive")),
                null);

        // Host header is managed by HttpClient and will reflect the backend host,
        // never the inbound "old-api.example.com". Connection is also managed by
        // HttpClient for HTTP/2 upgrade. What we verify: the user-supplied values
        // did not leak through.
        wireMock.verify(anyRequestedFor(urlEqualTo("/api/test"))
                .withHeader("Host", containing("localhost"))
                .withHeader("Authorization", equalTo("Bearer abc")));
    }

    @Test
    void noRouteMatchReturns404WithErrorJson() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/test", "/api/test", List.of("GET")));

        Response response = svc.proxy("GET", uriInfo("/no-match", Map.of()), headers(null, Map.of()), null);

        assertThat(response.getStatus()).isEqualTo(404);
    }

    @Test
    void forwardsPostRequestBodyUnchangedWhenNoBodyTransforms() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/resource", "/api/resource", List.of()));
        String jsonBody = "{\"name\":\"A\",\"price\":100}";

        wireMock.stubFor(post(urlEqualTo("/api/resource"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(equalToJson(jsonBody))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"id\":1}")));

        Response response = svc.proxy("POST", uriInfo("/resource", Map.of()),
                headers("application/json", Map.of()),
                jsonBody.getBytes(StandardCharsets.UTF_8));

        assertThat(response.getStatus()).isEqualTo(200);
        wireMock.verify(postRequestedFor(urlEqualTo("/api/resource")));
    }

    @Test
    void forwardsPutRequestBodyUnchangedWhenNoBodyTransforms() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/resource", "/api/resource", List.of()));
        String jsonBody = "{\"name\":\"Updated\"}";

        wireMock.stubFor(put(urlEqualTo("/api/resource"))
                .withRequestBody(equalToJson(jsonBody))
                .willReturn(aResponse().withStatus(200)));

        svc.proxy("PUT", uriInfo("/resource", Map.of()),
                headers("application/json", Map.of()),
                jsonBody.getBytes(StandardCharsets.UTF_8));

        wireMock.verify(WireMock.putRequestedFor(urlEqualTo("/api/resource")));
    }

    @Test
    void bodyToPathTransform() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/update-product", "/products/{code}", List.of("POST"),
                tx("body:/code", "path:code")));

        wireMock.stubFor(post(urlEqualTo("/products/ABC"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(equalToJson("{\"name\":\"Widget\"}"))
                .willReturn(aResponse().withStatus(200)));

        svc.proxy("POST", uriInfo("/update-product", Map.of()),
                headers("application/json", Map.of()),
                "{\"code\":\"ABC\",\"name\":\"Widget\"}".getBytes(StandardCharsets.UTF_8));

        wireMock.verify(postRequestedFor(urlEqualTo("/products/ABC")));
    }

    @Test
    void bodyFieldRename() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/users", "/api/users", List.of("POST"),
                tx("body:/userId", "body:/id")));

        wireMock.stubFor(post(urlEqualTo("/api/users"))
                .withRequestBody(equalToJson("{\"name\":\"A\",\"id\":\"u1\"}"))
                .willReturn(aResponse().withStatus(200)));

        svc.proxy("POST", uriInfo("/users", Map.of()),
                headers("application/json", Map.of()),
                "{\"userId\":\"u1\",\"name\":\"A\"}".getBytes(StandardCharsets.UTF_8));

        wireMock.verify(postRequestedFor(urlEqualTo("/api/users")));
    }

    @Test
    void bodyFieldDrop() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/create", "/api/create", List.of("POST"),
                txDrop("body:/debug")));

        wireMock.stubFor(post(urlEqualTo("/api/create"))
                .withRequestBody(equalToJson("{\"name\":\"X\"}"))
                .willReturn(aResponse().withStatus(200)));

        svc.proxy("POST", uriInfo("/create", Map.of()),
                headers("application/json", Map.of()),
                "{\"name\":\"X\",\"debug\":true}".getBytes(StandardCharsets.UTF_8));

        wireMock.verify(postRequestedFor(urlEqualTo("/api/create")));
    }

    @Test
    void wrapEnvelope() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/create", "/v2/create", List.of("POST"),
                "application/json",
                tx("body:", "body:/data")));

        wireMock.stubFor(post(urlEqualTo("/v2/create"))
                .withRequestBody(equalToJson("{\"data\":{\"sku\":\"X\"}}"))
                .willReturn(aResponse().withStatus(200)));

        svc.proxy("POST", uriInfo("/create", Map.of()),
                headers("application/json", Map.of()),
                "{\"sku\":\"X\"}".getBytes(StandardCharsets.UTF_8));

        wireMock.verify(postRequestedFor(urlEqualTo("/v2/create")));
    }

    @Test
    void unwrapEnvelope() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/submit", "/v1/submit", List.of("POST"),
                "application/json",
                tx("body:/payload", "body:")));

        wireMock.stubFor(post(urlEqualTo("/v1/submit"))
                .withRequestBody(equalToJson("{\"a\":1}"))
                .willReturn(aResponse().withStatus(200)));

        svc.proxy("POST", uriInfo("/submit", Map.of()),
                headers("application/json", Map.of()),
                "{\"payload\":{\"a\":1}}".getBytes(StandardCharsets.UTF_8));

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/submit")));
    }

    @Test
    void pathToBodyTransform() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/users/{userId}/orders", "/orders", List.of("POST"),
                "application/json",
                tx("path:userId", "body:/owner/id")));

        wireMock.stubFor(post(urlEqualTo("/orders"))
                .withRequestBody(equalToJson("{\"owner\":{\"id\":\"42\"}}"))
                .willReturn(aResponse().withStatus(200)));

        svc.proxy("POST", uriInfo("/users/42/orders", Map.of()),
                headers("application/json", Map.of()),
                "{}".getBytes(StandardCharsets.UTF_8));

        wireMock.verify(postRequestedFor(urlEqualTo("/orders")));
    }

    @Test
    void crossCodecFormInJsonOut() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/form-login", "/auth/token", List.of("POST"),
                "application/json",
                tx("body:/username", "body:/user"),
                tx("body:/password", "body:/credentials/secret")));

        wireMock.stubFor(post(urlEqualTo("/auth/token"))
                .withHeader("Content-Type", containing("application/json"))
                .withRequestBody(equalToJson("{\"user\":\"bob\",\"credentials\":{\"secret\":\"x\"}}"))
                .willReturn(aResponse().withStatus(200)));

        svc.proxy("POST", uriInfo("/form-login", Map.of()),
                headers("application/x-www-form-urlencoded", Map.of()),
                "username=bob&password=x".getBytes(StandardCharsets.UTF_8));

        wireMock.verify(postRequestedFor(urlEqualTo("/auth/token")));
    }

    @Test
    void unsupportedContentTypeWithBodyTransformsReturns415() {
        ProxyService svc = proxyServiceFor(route("r", "/x", "/y", List.of("POST"),
                tx("body:/a", "body:/b")));

        assertThatThrownBy(() -> svc.proxy("POST", uriInfo("/x", Map.of()),
                headers("text/plain", Map.of()),
                "raw bytes".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(id.adiputera.proxy.exception.BodyTransformException.class,
                        ex -> assertThat(ex.getStatus().getStatusCode()).isEqualTo(415));
    }

    @Test
    void bodyToHeaderLandsOnOutboundRequest() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/header-login", "/auth/token", List.of("POST"),
                "application/json",
                tx("body:/apiKey", "header:X-API-Key")));

        wireMock.stubFor(post(urlEqualTo("/auth/token"))
                .withHeader("X-API-Key", equalTo("secret123"))
                .withRequestBody(equalToJson("{\"user\":\"bob\"}"))
                .willReturn(aResponse().withStatus(200)));

        svc.proxy("POST", uriInfo("/header-login", Map.of()),
                headers("application/json", Map.of()),
                "{\"apiKey\":\"secret123\",\"user\":\"bob\"}".getBytes(StandardCharsets.UTF_8));

        wireMock.verify(postRequestedFor(urlEqualTo("/auth/token"))
                .withHeader("X-API-Key", equalTo("secret123")));
    }

    @Test
    void headerTransformOverridesInboundHeaderOfSameName() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/header-login", "/auth/token", List.of("POST"),
                "application/json",
                tx("body:/apiKey", "header:Authorization")));

        wireMock.stubFor(post(urlEqualTo("/auth/token"))
                .willReturn(aResponse().withStatus(200)));

        svc.proxy("POST", uriInfo("/header-login", Map.of()),
                headers("application/json", Map.of("Authorization", "Bearer OLD")),
                "{\"apiKey\":\"NEW\"}".getBytes(StandardCharsets.UTF_8));

        wireMock.verify(postRequestedFor(urlEqualTo("/auth/token"))
                .withHeader("Authorization", equalTo("NEW")));
    }

    @Test
    void headerToQueryMovesInboundHeaderToUrl() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/api", "/api", List.of("GET"),
                tx("header:X-Search", "query:q")));

        wireMock.stubFor(get(urlEqualTo("/api?q=shoes"))
                .willReturn(aResponse().withStatus(200)));

        svc.proxy("GET", uriInfo("/api", Map.of()),
                headers(null, Map.of("X-Search", "shoes")),
                null);

        wireMock.verify(anyRequestedFor(urlEqualTo("/api?q=shoes"))
                .withHeader("X-Search", absent()));
    }

    @Test
    void backendSuccessResponseBodyIsReturnedAsIs() throws Exception {
        ProxyService svc = proxyServiceFor(route("r", "/resource", "/api/resource", List.of("POST")));
        String responseBody = "{\"id\":42}";

        wireMock.stubFor(post(urlEqualTo("/api/resource"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

        Response response = svc.proxy("POST", uriInfo("/resource", Map.of()),
                headers("application/json", Map.of()),
                "{\"a\":1}".getBytes(StandardCharsets.UTF_8));

        assertThat(new String((byte[]) response.getEntity(), StandardCharsets.UTF_8)).isEqualTo(responseBody);
    }

    // reference to avoid "unused import" warnings
    @SuppressWarnings("unused")
    private static Object _stubReferences() {
        return delete(urlPathEqualTo("/"));
    }
}

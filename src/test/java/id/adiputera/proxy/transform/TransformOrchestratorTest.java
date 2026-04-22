package id.adiputera.proxy.transform;

import id.adiputera.proxy.exception.BodyTransformException;
import id.adiputera.proxy.model.ParamTransform;
import id.adiputera.proxy.model.RouteDefinition;
import id.adiputera.proxy.transform.body.BodyCodecRegistry;
import id.adiputera.proxy.transform.body.FormUrlEncodedBodyCodec;
import id.adiputera.proxy.transform.body.JsonBodyCodec;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransformOrchestratorTest {

    private TransformOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        BodyCodecRegistry registry = new BodyCodecRegistry(
                List.of(new JsonBodyCodec(), new FormUrlEncodedBodyCodec()));
        orchestrator = new TransformOrchestrator(registry, new RequestTransformer());
    }

    @Test
    void queryToPath() {
        RouteDefinition route = route("r", "/products/{code}",
                tx("query:code", "path:code"));
        TransformOrchestrator.Result r = apply(route, Map.of(),
                Map.of("code", new String[]{"ABC"}), null, null);

        assertThat(r.targetUri().toString()).isEqualTo("https://be.test/products/ABC");
    }

    @Test
    void pathToQuery() {
        RouteDefinition route = route("r", "/products/search",
                tx("path:keyword", "query:q"));
        TransformOrchestrator.Result r = apply(route,
                Map.of("keyword", "shoes"), Map.of(), null, null);

        assertThat(r.targetUri().toString()).isEqualTo("https://be.test/products/search?q=shoes");
    }

    @Test
    void pathToPathRename() {
        RouteDefinition route = route("r", "/products/{productId}",
                tx("path:id", "path:productId"));
        TransformOrchestrator.Result r = apply(route,
                Map.of("id", "ABC"), Map.of(), null, null);

        assertThat(r.targetUri().toString()).isEqualTo("https://be.test/products/ABC");
    }

    @Test
    void queryToQueryRename() {
        RouteDefinition route = route("r", "/auth/token",
                tx("query:username", "query:user"));
        TransformOrchestrator.Result r = apply(route, Map.of(),
                Map.of("username", new String[]{"bob"}), null, null);

        assertThat(r.targetUri().toString()).isEqualTo("https://be.test/auth/token?user=bob");
    }

    @Test
    void queryToBodyWritesField() {
        RouteDefinition route = route("r", "/checkout", "application/json",
                tx("query:coupon", "body:/promo/code"));

        TransformOrchestrator.Result r = apply(route, Map.of(),
                Map.of("coupon", new String[]{"X"}),
                "{}".getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_TYPE);

        assertThat(new String(r.forwardBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"promo\":{\"code\":\"X\"}}");
        assertThat(r.targetUri().toString()).isEqualTo("https://be.test/checkout");
    }

    @Test
    void pathToBodyWritesField() {
        RouteDefinition route = route("r", "/orders", "application/json",
                tx("path:userId", "body:/owner/id"));

        TransformOrchestrator.Result r = apply(route,
                Map.of("userId", "42"), Map.of(),
                "{}".getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_TYPE);

        assertThat(new String(r.forwardBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"owner\":{\"id\":\"42\"}}");
    }

    @Test
    void bodyToPathMovesValue() {
        RouteDefinition route = route("r", "/products/{code}",
                tx("body:/code", "path:code"));

        TransformOrchestrator.Result r = apply(route, Map.of(), Map.of(),
                "{\"code\":\"ABC\",\"name\":\"X\"}".getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_TYPE);

        assertThat(r.targetUri().toString()).isEqualTo("https://be.test/products/ABC");
        assertThat(new String(r.forwardBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"name\":\"X\"}");
    }

    @Test
    void bodyToQueryMovesValue() {
        RouteDefinition route = route("r", "/search",
                tx("body:/keyword", "query:q"));

        TransformOrchestrator.Result r = apply(route, Map.of(), Map.of(),
                "{\"keyword\":\"shoes\",\"page\":1}".getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_TYPE);

        assertThat(r.targetUri().toString()).isEqualTo("https://be.test/search?q=shoes");
        assertThat(new String(r.forwardBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"page\":1}");
    }

    @Test
    void bodyToBodyRename() {
        RouteDefinition route = route("r", "/users",
                tx("body:/userId", "body:/id"));

        TransformOrchestrator.Result r = apply(route, Map.of(), Map.of(),
                "{\"userId\":\"u1\",\"name\":\"A\"}".getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_TYPE);

        assertThat(new String(r.forwardBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"name\":\"A\",\"id\":\"u1\"}");
    }

    @Test
    void bodyDrop() {
        RouteDefinition route = route("r", "/products", txDrop("body:/debug"));

        TransformOrchestrator.Result r = apply(route, Map.of(), Map.of(),
                "{\"name\":\"X\",\"debug\":true}".getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_TYPE);

        assertThat(new String(r.forwardBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"name\":\"X\"}");
    }

    @Test
    void wrapEnvelope() {
        RouteDefinition route = route("r", "/v2/products", "application/json",
                tx("body:", "body:/data"));

        TransformOrchestrator.Result r = apply(route, Map.of(), Map.of(),
                "{\"sku\":\"X\"}".getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_TYPE);

        assertThat(new String(r.forwardBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"data\":{\"sku\":\"X\"}}");
    }

    @Test
    void unwrapEnvelope() {
        RouteDefinition route = route("r", "/v1/submit", "application/json",
                tx("body:/payload", "body:"));

        TransformOrchestrator.Result r = apply(route, Map.of(), Map.of(),
                "{\"payload\":{\"a\":1}}".getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_TYPE);

        assertThat(new String(r.forwardBody(), StandardCharsets.UTF_8)).isEqualTo("{\"a\":1}");
    }

    @Test
    void formInJsonOutCrossCodec() {
        RouteDefinition route = route("r", "/auth/token", "application/json",
                tx("body:/username", "body:/user"),
                tx("body:/password", "body:/credentials/secret"));

        TransformOrchestrator.Result r = apply(route, Map.of(), Map.of(),
                "username=bob&password=x".getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_FORM_URLENCODED_TYPE);

        assertThat(r.forwardContentType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
        assertThat(new String(r.forwardBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"user\":\"bob\",\"credentials\":{\"secret\":\"x\"}}");
    }

    @Test
    void noBodyTransformsPreservesRawBytes() {
        RouteDefinition route = route("r", "/api/resource");
        byte[] raw = "{\"original\":true}".getBytes(StandardCharsets.UTF_8);

        TransformOrchestrator.Result r = apply(route, Map.of(), Map.of(),
                raw, MediaType.APPLICATION_JSON_TYPE);

        assertThat(r.forwardBody()).isSameAs(raw);
        assertThat(r.forwardContentType()).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    void unsupportedContentTypeWithBodyTransformsThrows415() {
        RouteDefinition route = route("r", "/x",
                tx("body:/a", "body:/b"));

        assertThatThrownBy(() -> apply(route, Map.of(), Map.of(),
                "ignored".getBytes(StandardCharsets.UTF_8),
                MediaType.TEXT_PLAIN_TYPE))
                .isInstanceOf(BodyTransformException.class)
                .satisfies(ex -> assertThat(((BodyTransformException) ex).getStatus().getStatusCode()).isEqualTo(415));
    }

    @Test
    void queryToHeader() {
        RouteDefinition route = route("r", "/auth/token",
                tx("query:token", "header:Authorization"));
        TransformOrchestrator.Result r = apply(route, Map.of(),
                Map.of("token", new String[]{"abc123"}), null, null);

        assertThat(r.addHeaders()).containsEntry("Authorization", "abc123");
        assertThat(r.removeHeaders()).contains("authorization");
        assertThat(r.targetUri().toString()).isEqualTo("https://be.test/auth/token");
    }

    @Test
    void pathToHeader() {
        RouteDefinition route = route("r", "/auth/token",
                tx("path:userId", "header:X-User-Id"));
        TransformOrchestrator.Result r = apply(route,
                Map.of("userId", "42"), Map.of(), null, null);

        assertThat(r.addHeaders()).containsEntry("X-User-Id", "42");
        assertThat(r.removeHeaders()).contains("x-user-id");
        assertThat(r.targetUri().toString()).isEqualTo("https://be.test/auth/token");
    }

    @Test
    void bodyToHeader() {
        RouteDefinition route = route("r", "/auth/token", "application/json",
                tx("body:/apiKey", "header:X-API-Key"));

        TransformOrchestrator.Result r = apply(route, Map.of(), Map.of(),
                "{\"apiKey\":\"secret\",\"name\":\"A\"}".getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_TYPE);

        assertThat(r.addHeaders()).containsEntry("X-API-Key", "secret");
        assertThat(r.removeHeaders()).contains("x-api-key");
        assertThat(new String(r.forwardBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"name\":\"A\"}");
    }

    @Test
    void headerToQuery() {
        RouteDefinition route = route("r", "/search",
                tx("header:X-Search", "query:q"));
        TransformOrchestrator.Result r = apply(route, Map.of(), Map.of(), null, null,
                Map.of("X-Search", java.util.List.of("shoes")));

        assertThat(r.targetUri().toString()).isEqualTo("https://be.test/search?q=shoes");
        assertThat(r.removeHeaders()).contains("x-search");
    }

    @Test
    void headerToQueryIsCaseInsensitiveOnRead() {
        RouteDefinition route = route("r", "/search",
                tx("header:x-search", "query:q"));
        TransformOrchestrator.Result r = apply(route, Map.of(), Map.of(), null, null,
                Map.of("X-Search", java.util.List.of("shoes")));

        assertThat(r.targetUri().toString()).isEqualTo("https://be.test/search?q=shoes");
    }

    @Test
    void headerToPath() {
        RouteDefinition route = route("r", "/users/{userId}",
                tx("header:X-User-Id", "path:userId"));
        TransformOrchestrator.Result r = apply(route, Map.of(), Map.of(), null, null,
                Map.of("X-User-Id", java.util.List.of("42")));

        assertThat(r.targetUri().toString()).isEqualTo("https://be.test/users/42");
        assertThat(r.removeHeaders()).contains("x-user-id");
    }

    @Test
    void headerToBody() {
        RouteDefinition route = route("r", "/submit", "application/json",
                tx("header:X-Trace-Id", "body:/trace/id"));
        TransformOrchestrator.Result r = apply(route, Map.of(), Map.of(),
                "{}".getBytes(StandardCharsets.UTF_8),
                MediaType.APPLICATION_JSON_TYPE,
                Map.of("X-Trace-Id", java.util.List.of("t-99")));

        assertThat(new String(r.forwardBody(), StandardCharsets.UTF_8))
                .isEqualTo("{\"trace\":{\"id\":\"t-99\"}}");
        assertThat(r.removeHeaders()).contains("x-trace-id");
    }

    @Test
    void headerToHeaderRename() {
        RouteDefinition route = route("r", "/auth/token",
                tx("header:X-Legacy-Token", "header:Authorization"));
        TransformOrchestrator.Result r = apply(route, Map.of(), Map.of(), null, null,
                Map.of("X-Legacy-Token", java.util.List.of("bearer-xyz")));

        assertThat(r.addHeaders()).containsEntry("Authorization", "bearer-xyz");
        assertThat(r.removeHeaders()).contains("x-legacy-token", "authorization");
    }

    @Test
    void headerMissingInboundValueNoOp() {
        RouteDefinition route = route("r", "/auth/token",
                tx("header:X-API-Key", "query:apiKey"));
        TransformOrchestrator.Result r = apply(route, Map.of(), Map.of(), null, null, Map.of());

        assertThat(r.targetUri().toString()).isEqualTo("https://be.test/auth/token");
        assertThat(r.removeHeaders()).contains("x-api-key");
    }

    @Test
    void passthroughQueryParamRemainsOnOutboundUri() {
        RouteDefinition route = route("r", "/products/{code}",
                tx("query:code", "path:code"));

        TransformOrchestrator.Result r = apply(route, Map.of(),
                Map.of("code", new String[]{"ABC"}, "lang", new String[]{"en"}),
                null, null);

        assertThat(r.targetUri().toString()).isEqualTo("https://be.test/products/ABC?lang=en");
    }

    private TransformOrchestrator.Result apply(RouteDefinition route,
                                               Map<String, String> pathVars,
                                               Map<String, String[]> queryParams,
                                               byte[] body,
                                               MediaType contentType) {
        return apply(route, pathVars, queryParams, body, contentType, Map.of());
    }

    private TransformOrchestrator.Result apply(RouteDefinition route,
                                               Map<String, String> pathVars,
                                               Map<String, String[]> queryParams,
                                               byte[] body,
                                               MediaType contentType,
                                               Map<String, java.util.List<String>> headers) {
        return orchestrator.apply(route, pathVars, queryParams, body, contentType, headers, "https://be.test");
    }

    private static RouteDefinition route(String id, String target, ParamTransform... transforms) {
        RouteDefinition r = new RouteDefinition();
        r.setId(id);
        r.setTarget(target);
        r.setTransforms(new ArrayList<>(Arrays.asList(transforms)));
        return r;
    }

    private static RouteDefinition route(String id, String target, String produces, ParamTransform... transforms) {
        RouteDefinition r = route(id, target, transforms);
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
}

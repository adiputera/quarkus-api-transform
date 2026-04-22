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
        return orchestrator.apply(route, pathVars, queryParams, body, contentType, "https://be.test");
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

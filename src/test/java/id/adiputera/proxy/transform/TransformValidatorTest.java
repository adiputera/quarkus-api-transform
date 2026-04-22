package id.adiputera.proxy.transform;

import id.adiputera.proxy.model.ParamTransform;
import id.adiputera.proxy.model.RouteDefinition;
import id.adiputera.proxy.transform.body.BodyCodecRegistry;
import id.adiputera.proxy.transform.body.FormUrlEncodedBodyCodec;
import id.adiputera.proxy.transform.body.JsonBodyCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TransformValidatorTest {

    private TransformValidator validator;

    @BeforeEach
    void setUp() {
        BodyCodecRegistry registry = new BodyCodecRegistry(
                List.of(new JsonBodyCodec(), new FormUrlEncodedBodyCodec()));
        validator = new TransformValidator(registry);
    }

    @Test
    void rejectsInvalidLocationPrefix() {
        RouteDefinition route = route("r", "/x", tx("cookie:x", "query:x"));
        assertThatThrownBy(() -> validator.validate(List.of(route)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cookie");
    }

    @Test
    void acceptsHeaderLocations() {
        RouteDefinition routeTo = route("r1", "/x",
                tx("query:token", "header:Authorization"));
        RouteDefinition routeFrom = route("r2", "/x",
                tx("header:X-API-Key", "body:/apiKey"));
        assertThatCode(() -> validator.validate(List.of(routeTo, routeFrom)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsEmptyHeaderNameOnFrom() {
        RouteDefinition route = route("r", "/x", tx("header:", "query:x"));
        assertThatThrownBy(() -> validator.validate(List.of(route)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("header name must not be empty");
    }

    @Test
    void rejectsEmptyHeaderNameOnTo() {
        RouteDefinition route = route("r", "/x", tx("query:x", "header:"));
        assertThatThrownBy(() -> validator.validate(List.of(route)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("header name must not be empty");
    }

    @Test
    void bodyToHeaderDoesNotRequireProduces() {
        RouteDefinition route = route("r", "/x", tx("body:/apiKey", "header:X-API-Key"));
        assertThatCode(() -> validator.validate(List.of(route)))
                .doesNotThrowAnyException();
    }

    @Test
    void headerToBodyDoesNotRequireProducesForTopLevel() {
        RouteDefinition route = route("r", "/x", tx("header:X-API-Key", "body:/apiKey"));
        assertThatCode(() -> validator.validate(List.of(route)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDropOnNonBody() {
        RouteDefinition route = route("r", "/x", txDrop("query:x"));
        assertThatThrownBy(() -> validator.validate(List.of(route)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("omitted");
    }

    @Test
    void rejectsMissingPathPlaceholder() {
        RouteDefinition route = route("r", "/products", tx("body:/code", "path:code"));
        assertThatThrownBy(() -> validator.validate(List.of(route)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{code}");
    }

    @Test
    void rejectsNestedPointerWithoutProduces() {
        RouteDefinition route = route("r", "/x", tx("query:coupon", "body:/promo/code"));
        assertThatThrownBy(() -> validator.validate(List.of(route)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("nested pointer");
    }

    @Test
    void rejectsRootPointerWithoutProduces() {
        RouteDefinition route = route("r", "/x", tx("body:", "body:/data"));
        assertThatThrownBy(() -> validator.validate(List.of(route)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wrap/unwrap");
    }

    @Test
    void rejectsRootPointerWithFormProduces() {
        RouteDefinition route = route("r", "/x",
                "application/x-www-form-urlencoded",
                tx("body:", "body:/data"));
        assertThatThrownBy(() -> validator.validate(List.of(route)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wrap/unwrap");
    }

    @Test
    void acceptsTopLevelBodyRenameWithoutProduces() {
        RouteDefinition route = route("r", "/x", tx("body:/a", "body:/b"));
        assertThatCode(() -> validator.validate(List.of(route))).doesNotThrowAnyException();
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

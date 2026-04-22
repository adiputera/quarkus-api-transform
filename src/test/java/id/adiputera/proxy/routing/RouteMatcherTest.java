package id.adiputera.proxy.routing;

import id.adiputera.proxy.config.ConfigProvider;
import id.adiputera.proxy.config.ConfigSnapshot;
import id.adiputera.proxy.model.ParamTransform;
import id.adiputera.proxy.model.RouteDefinition;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RouteMatcherTest {

    private static RouteMatcher matcherFor(RouteDefinition... routes) {
        ConfigSnapshot snapshot = new ConfigSnapshot(
                RouteCompiler.compile(List.of(routes)),
                Map.of(), Map.of(), Map.of(), List.of(), List.of(), 5000, 30000);
        ConfigProvider provider = Mockito.mock(ConfigProvider.class);
        Mockito.when(provider.current()).thenReturn(snapshot);
        return new RouteMatcher(provider);
    }

    @Test
    void matchesSimplePath() {
        RouteMatcher matcher = matcherFor(
                route("r1", "/get-products", "/products", List.of("GET"), List.of()));

        RouteMatcher.MatchResult result = matcher.match("GET", "/get-products", Set.of());

        assertThat(result).isNotNull();
        assertThat(result.route().getId()).isEqualTo("r1");
    }

    @Test
    void matchesPathVariable() {
        RouteMatcher matcher = matcherFor(
                route("r1", "/get-products/{code}", "/products/{code}", List.of("GET"), List.of()));

        RouteMatcher.MatchResult result = matcher.match("GET", "/get-products/ABC123", Set.of());

        assertThat(result).isNotNull();
        assertThat(result.pathVariables()).containsEntry("code", "ABC123");
    }

    @Test
    void rejectsWrongMethod() {
        RouteMatcher matcher = matcherFor(
                route("r1", "/get-products", "/products", List.of("GET"), List.of()));

        RouteMatcher.MatchResult result = matcher.match("POST", "/get-products", Set.of());

        assertThat(result).isNull();
    }

    @Test
    void returnsNullWhenNoMatch() {
        RouteMatcher matcher = matcherFor(
                route("r1", "/get-products", "/products", List.of("GET"), List.of()));

        RouteMatcher.MatchResult result = matcher.match("GET", "/unknown", Set.of());

        assertThat(result).isNull();
    }

    @Test
    void matchesRouteWithTransformWhenQueryParamPresent() {
        ParamTransform transform = new ParamTransform();
        transform.setFrom("query:code");
        transform.setTo("path:code");

        RouteMatcher matcher = matcherFor(
                route("simple", "/get-products", "/products", List.of("GET"), List.of()),
                route("with-transform", "/get-products", "/products/{code}", List.of("GET"), List.of(transform)));

        RouteMatcher.MatchResult result = matcher.match("GET", "/get-products", Set.of("code"));

        assertThat(result).isNotNull();
        assertThat(result.route().getId()).isEqualTo("with-transform");
    }

    @Test
    void fallsBackToSimpleRouteWhenQueryParamMissing() {
        ParamTransform transform = new ParamTransform();
        transform.setFrom("query:code");
        transform.setTo("path:code");

        RouteMatcher matcher = matcherFor(
                route("simple", "/get-products", "/products", List.of("GET"), List.of()),
                route("with-transform", "/get-products", "/products/{code}", List.of("GET"), List.of(transform)));

        RouteMatcher.MatchResult result = matcher.match("GET", "/get-products", Set.of());

        assertThat(result).isNotNull();
        assertThat(result.route().getId()).isEqualTo("simple");
    }

    @Test
    void specificRouteMatchesOverWildcard() {
        RouteMatcher matcher = matcherFor(
                route("wildcard", "/api/old-services/**", "/api/new-services/**", List.of(), List.of()),
                route("specific", "/api/old-services/get-products", "/api/new-services/products", List.of("GET"), List.of()));

        RouteMatcher.MatchResult result = matcher.match("GET", "/api/old-services/get-products", Set.of());

        assertThat(result).isNotNull();
        assertThat(result.route().getId()).isEqualTo("specific");
    }

    @Test
    void wildcardMatchesWhenNoSpecificRoute() {
        RouteMatcher matcher = matcherFor(
                route("wildcard", "/api/old-services/**", "/api/new-services/**", List.of(), List.of()),
                route("specific", "/api/old-services/get-products", "/api/new-services/products", List.of("GET"), List.of()));

        RouteMatcher.MatchResult result = matcher.match("GET", "/api/old-services/orders/123", Set.of());

        assertThat(result).isNotNull();
        assertThat(result.route().getId()).isEqualTo("wildcard");
    }

    @Test
    void wildcardCapturesSuffixAsPathVariable() {
        RouteMatcher matcher = matcherFor(
                route("wildcard", "/api/old-services/**", "/api/new-services/**", List.of(), List.of()));

        RouteMatcher.MatchResult result = matcher.match("GET", "/api/old-services/orders/123/items", Set.of());

        assertThat(result).isNotNull();
        assertThat(result.pathVariables()).containsEntry("_suffix", "/orders/123/items");
    }

    @Test
    void specificRouteWithPathVarOverWildcard() {
        RouteMatcher matcher = matcherFor(
                route("wildcard", "/api/old-services/**", "/api/new-services/**", List.of(), List.of()),
                route("specific", "/api/old-services/products/{code}", "/api/new-services/products/{code}", List.of("GET"), List.of()));

        RouteMatcher.MatchResult result = matcher.match("GET", "/api/old-services/products/ABC123", Set.of());

        assertThat(result).isNotNull();
        assertThat(result.route().getId()).isEqualTo("specific");
        assertThat(result.pathVariables()).containsEntry("code", "ABC123");
    }

    private RouteDefinition route(String id, String source, String target, List<String> methods, List<ParamTransform> transforms) {
        RouteDefinition route = new RouteDefinition();
        route.setId(id);
        route.setSource(source);
        route.setTarget(target);
        route.setMethods(methods);
        route.setTransforms(transforms);
        return route;
    }
}

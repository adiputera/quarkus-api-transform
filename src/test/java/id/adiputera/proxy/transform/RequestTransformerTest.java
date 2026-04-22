package id.adiputera.proxy.transform;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RequestTransformerTest {

    private final RequestTransformer transformer = new RequestTransformer();

    @Test
    void simplePathMapping() {
        URI result = transformer.buildTargetUrl(
                "https://backend.example.com", "/products",
                Map.of(), Map.of());

        assertThat(result.toString()).isEqualTo("https://backend.example.com/products");
    }

    @Test
    void pathVariableSubstitution() {
        URI result = transformer.buildTargetUrl(
                "https://backend.example.com", "/products/{code}",
                Map.of("code", "ABC123"), Map.of());

        assertThat(result.toString()).isEqualTo("https://backend.example.com/products/ABC123");
    }

    @Test
    void multiplePathVariables() {
        URI result = transformer.buildTargetUrl(
                "https://backend.example.com", "/products/{categories}/{code}",
                Map.of("categories", "electronics", "code", "ABC123"),
                Map.of());

        assertThat(result.toString()).isEqualTo("https://backend.example.com/products/electronics/ABC123");
    }

    @Test
    void queryParamsAppended() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page", "2");
        query.put("size", "10");

        URI result = transformer.buildTargetUrl(
                "https://backend.example.com", "/products",
                Map.of(), query);

        assertThat(result.toString()).isEqualTo("https://backend.example.com/products?page=2&size=10");
    }

    @Test
    void pathAndQueryCombined() {
        URI result = transformer.buildTargetUrl(
                "https://backend.example.com", "/products/{code}",
                Map.of("code", "ABC"),
                Map.of("lang", "en"));

        assertThat(result.toString()).isEqualTo("https://backend.example.com/products/ABC?lang=en");
    }

    @Test
    void wildcardSuffixAppended() {
        URI result = transformer.buildTargetUrl(
                "https://backend.example.com", "/api/new-services/**",
                Map.of("_suffix", "/foo/bar"),
                Map.of());

        assertThat(result.toString()).isEqualTo("https://backend.example.com/api/new-services/foo/bar");
    }

    @Test
    void wildcardDeepNested() {
        URI result = transformer.buildTargetUrl(
                "https://backend.example.com", "/api/new/**",
                Map.of("_suffix", "/a/b/c/d"),
                Map.of());

        assertThat(result.toString()).isEqualTo("https://backend.example.com/api/new/a/b/c/d");
    }

    @Test
    void wildcardWithQueryParams() {
        Map<String, String> query = new LinkedHashMap<>();
        query.put("page", "1");
        query.put("size", "10");

        URI result = transformer.buildTargetUrl(
                "https://backend.example.com", "/api/new-services/**",
                Map.of("_suffix", "/products"),
                query);

        assertThat(result.toString())
                .isEqualTo("https://backend.example.com/api/new-services/products?page=1&size=10");
    }

    @Test
    void suffixPathVarNotTreatedAsPlaceholder() {
        URI result = transformer.buildTargetUrl(
                "https://backend.example.com", "/api/new/**",
                Map.of("_suffix", "/x"),
                Map.of());

        assertThat(result.toString()).isEqualTo("https://backend.example.com/api/new/x");
    }
}

package id.adiputera.proxy.proxy;

import id.adiputera.proxy.config.ConfigSnapshot;
import id.adiputera.proxy.model.BackendDefinition;
import id.adiputera.proxy.routing.RouteCompiler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ResponseHeaderRewriter}.
 *
 * <p>Verifies Location header rewriting for redirects pointing inside and outside
 * configured backend domains.</p>
 *
 * @author Yusuf F. Adiputera
 */
class ResponseHeaderRewriterTest {

    private ResponseHeaderRewriter rewriter;
    private ConfigSnapshot snapshot;

    /**
     * Initializes rewriter and sample config snapshot before each test.
     */
    @BeforeEach
    void setUp() {
        rewriter = new ResponseHeaderRewriter();

        BackendDefinition b1 = new BackendDefinition();
        b1.setBaseUrl("http://internal-api:8080");

        BackendDefinition b2 = new BackendDefinition();
        b2.setBaseUrl("https://secure-backend.internal");

        snapshot = new ConfigSnapshot(
                RouteCompiler.compile(List.of()),
                Map.of("b1", b1, "b2", b2),
                Map.of("b1", HttpClient.newHttpClient(), "b2", HttpClient.newHttpClient()),
                Map.of(),
                List.of(),
                List.of(),
                5000, 30000);
    }

    @Test
    void nullOrEmptyHeadersReturnsSame() {
        assertThat(rewriter.rewriteLocation(null, snapshot)).isNull();
        assertThat(rewriter.rewriteLocation(Collections.emptyMap(), snapshot)).isEmpty();
    }

    @Test
    void noLocationHeaderReturnsSame() {
        Map<String, List<String>> headers = Map.of("Content-Type", List.of("application/json"));
        Map<String, List<String>> result = rewriter.rewriteLocation(headers, snapshot);
        assertThat(result).isSameAs(headers);
    }

    @Test
    void relativeLocationReturnsSame() {
        Map<String, List<String>> headers = Map.of("Location", List.of("/dashboard?refresh=true"));
        Map<String, List<String>> result = rewriter.rewriteLocation(headers, snapshot);
        assertThat(result).isSameAs(headers);
    }

    @Test
    void locationToExternalDomainReturnsSame() {
        Map<String, List<String>> headers = Map.of("Location", List.of("https://oauth.google.com/auth"));
        Map<String, List<String>> result = rewriter.rewriteLocation(headers, snapshot);
        assertThat(result).isSameAs(headers);
    }

    @Test
    void locationToMatchingBackendRewritesToRelativePathAndQuery() {
        Map<String, List<String>> headers = Map.of("Location", List.of("http://internal-api:8080/users/42?tab=info#section"));
        Map<String, List<String>> result = rewriter.rewriteLocation(headers, snapshot);

        assertThat(result).isNotSameAs(headers);
        assertThat(result).containsEntry("Location", List.of("/users/42?tab=info#section"));
    }

    @Test
    void locationToMatchingBackendPreservesOtherHeaders() {
        Map<String, List<String>> headers = Map.of(
                "Content-Type", List.of("text/html"),
                "Location", List.of("https://secure-backend.internal/welcome"),
                "Set-Cookie", List.of("session=abc", "track=xyz")
        );
        Map<String, List<String>> result = rewriter.rewriteLocation(headers, snapshot);

        assertThat(result)
                .containsEntry("Content-Type", List.of("text/html"))
                .containsEntry("Location", List.of("/welcome"))
                .containsEntry("Set-Cookie", List.of("session=abc", "track=xyz"));
    }
}

package id.adiputera.proxy.sample;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DummySecuredResource}.
 *
 * <p>Verifies 401 response when token is missing, 403 when token is invalid,
 * and 200 when token matches {@link DummyOauth2Resource#VALID_TOKEN}.</p>
 *
 * @author Yusuf F. Adiputera
 */
class DummySecuredResourceTest {

    private DummySecuredResource resource;

    /**
     * Initializes the secured resource instance before each test.
     */
    @BeforeEach
    void setUp() {
        resource = new DummySecuredResource();
    }

    @Test
    void missingHeaderReturns401() {
        Response response = resource.getSecured(null);
        assertThat(response.getStatus()).isEqualTo(401);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity.get("error")).isEqualTo("unauthorized");
    }

    @Test
    void blankHeaderReturns401() {
        Response response = resource.getSecured("   ");
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    void invalidTokenReturns403() {
        Response response = resource.getSecured("Bearer wrong-token-xyz");
        assertThat(response.getStatus()).isEqualTo(403);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity.get("error")).isEqualTo("access_denied");
    }

    @Test
    void validTokenReturns200() {
        Response response = resource.getSecured("Bearer " + DummyOauth2Resource.VALID_TOKEN);
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity.get("status")).isEqualTo("success");
    }

    @Test
    void validTokenWithoutBearerPrefixReturns200() {
        Response response = resource.postSecured(DummyOauth2Resource.VALID_TOKEN);
        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity.get("method")).isEqualTo("POST");
    }
}

package id.adiputera.proxy.sample;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DummyOauth2Resource}.
 *
 * <p>Verifies token issuing behavior for valid and invalid credentials under both
 * {@code HEADER} and {@code BODY} authentication modes.</p>
 *
 * @author Yusuf F. Adiputera
 */
class DummyOauth2ResourceTest {

    private DummyOauth2Resource resource;

    /**
     * Initializes the dummy OAuth2 resource instance before each test.
     */
    @BeforeEach
    void setUp() {
        resource = new DummyOauth2Resource();
    }

    @Test
    void validBodyCredentialsReturnsToken() {
        Response response = resource.token("client_credentials", DummyOauth2Resource.VALID_CLIENT_ID,
                DummyOauth2Resource.VALID_CLIENT_SECRET, null);

        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity).isNotNull();
        assertThat(entity.get("access_token").toString()).isEqualTo(DummyOauth2Resource.VALID_TOKEN);
        assertThat(entity.get("token_type")).isEqualTo("Bearer");
        assertThat(entity.get("expires_in")).isEqualTo(3600);
    }

    @Test
    void validHeaderCredentialsReturnsToken() {
        String basicHeader = "Basic " + Base64.getEncoder().encodeToString(
                (DummyOauth2Resource.VALID_CLIENT_ID + ":" + DummyOauth2Resource.VALID_CLIENT_SECRET)
                        .getBytes(StandardCharsets.UTF_8));
        Response response = resource.token("client_credentials", null, null, basicHeader);

        assertThat(response.getStatus()).isEqualTo(200);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity).isNotNull();
        assertThat(entity.get("access_token").toString()).isEqualTo(DummyOauth2Resource.VALID_TOKEN);
        assertThat(entity.get("token_type")).isEqualTo("Bearer");
    }

    @Test
    void unsupportedGrantTypeReturns401() {
        Response response = resource.token("password", DummyOauth2Resource.VALID_CLIENT_ID,
                DummyOauth2Resource.VALID_CLIENT_SECRET, null);

        assertThat(response.getStatus()).isEqualTo(401);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity.get("error")).isEqualTo("unsupported_grant_type");
    }

    @Test
    void missingCredentialsReturns401() {
        Response response = resource.token("client_credentials", "", "", null);

        assertThat(response.getStatus()).isEqualTo(401);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity.get("error")).isEqualTo("invalid_client");
    }

    @Test
    void invalidCredentialsReturns401() {
        Response response = resource.token("client_credentials", "wrong-id", "wrong-secret", null);

        assertThat(response.getStatus()).isEqualTo(401);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity.get("error")).isEqualTo("invalid_client");
    }

    @Test
    void invalidBase64HeaderReturns401() {
        Response response = resource.token("client_credentials", null, null, "Basic not-valid-base64@!");

        assertThat(response.getStatus()).isEqualTo(401);
        @SuppressWarnings("unchecked")
        Map<String, Object> entity = (Map<String, Object>) response.getEntity();
        assertThat(entity.get("error")).isEqualTo("invalid_client");
    }
}

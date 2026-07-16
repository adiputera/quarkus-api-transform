package id.adiputera.proxy.sample;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sample dummy OAuth2 authorization server resource simulating {@code client_credentials}
 * token issuing for local development and sample testing.
 *
 * <p>Supports both credential presentation styles:</p>
 * <ul>
 *   <li>{@code HEADER} mode: credentials extracted from {@code Authorization: Basic ...}</li>
 *   <li>{@code BODY} mode: credentials extracted from form parameters {@code client_id}/{@code client_secret}</li>
 * </ul>
 *
 * @author Yusuf F. Adiputera
 */
@Path("/sample/oauth2/token")
@ApplicationScoped
public class DummyOauth2Resource {

    /**
     * Constant access token returned by this dummy OAuth2 endpoint for verification across secured controllers.
     */
    public static final String VALID_TOKEN = "dummy-valid-access-token-12345";

    /**
     * Constant valid client ID expected by this dummy OAuth2 endpoint.
     */
    public static final String VALID_CLIENT_ID = "demo-client";

    /**
     * Constant valid client secret expected by this dummy OAuth2 endpoint.
     */
    public static final String VALID_CLIENT_SECRET = "demo-secret";

    /**
     * Issues a dummy OAuth2 access token upon validating client credentials and grant type.
     *
     * @param grantType          The OAuth2 grant type form parameter.
     * @param formClientId       The client ID form parameter (if in BODY mode).
     * @param formClientSecret   The client secret form parameter (if in BODY mode).
     * @param authorizationHeader The HTTP Authorization header (if in HEADER mode).
     * @return A Response containing JSON token payload on success, or 401 Unauthorized on failure.
     */
    @POST
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response token(@FormParam("grant_type") String grantType,
                          @FormParam("client_id") String formClientId,
                          @FormParam("client_secret") String formClientSecret,
                          @HeaderParam("Authorization") String authorizationHeader) {
        if (!"client_credentials".equals(grantType)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "unsupported_grant_type");
            err.put("error_description", "Only client_credentials grant type is supported by this dummy endpoint");
            return Response.status(Response.Status.UNAUTHORIZED).entity(err).build();
        }

        String clientId = formClientId;
        String clientSecret = formClientSecret;

        if (authorizationHeader != null && authorizationHeader.toLowerCase().startsWith("basic ")) {
            String base64Credentials = authorizationHeader.substring(6).trim();
            try {
                byte[] decoded = Base64.getDecoder().decode(base64Credentials);
                String decodedStr = new String(decoded, StandardCharsets.UTF_8);
                int colonIdx = decodedStr.indexOf(':');
                if (colonIdx >= 0) {
                    clientId = decodedStr.substring(0, colonIdx);
                    clientSecret = decodedStr.substring(colonIdx + 1);
                } else {
                    clientId = decodedStr;
                    clientSecret = "";
                }
            } catch (IllegalArgumentException e) {
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("error", "invalid_client");
                err.put("error_description", "Invalid Base64 format in Authorization header");
                return Response.status(Response.Status.UNAUTHORIZED).entity(err).build();
            }
        }

        if (!VALID_CLIENT_ID.equals(clientId) || !VALID_CLIENT_SECRET.equals(clientSecret)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "invalid_client");
            err.put("error_description", "Invalid client_id or client_secret credentials");
            return Response.status(Response.Status.UNAUTHORIZED).entity(err).build();
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("access_token", VALID_TOKEN);
        r.put("token_type", "Bearer");
        r.put("expires_in", 3600);
        return Response.ok(r).build();
    }
}

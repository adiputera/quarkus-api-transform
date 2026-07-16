package id.adiputera.proxy.sample;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sample secured resource endpoint expecting a valid access token.
 *
 * <p>Returns {@code 401 Unauthorized} when the token is missing, {@code 403 Forbidden}
 * when the token is invalid, and {@code 200 OK} when the token matches {@link DummyOauth2Resource#VALID_TOKEN}.</p>
 *
 * @author Yusuf F. Adiputera
 */
@Path("/sample/secured")
@ApplicationScoped
public class DummySecuredResource {

    /**
     * Handles GET requests to the secured endpoint and verifies the authorization token.
     *
     * @param authorizationHeader The HTTP Authorization header.
     * @return A Response with status 200 on valid token, 401 if missing, or 403 if invalid.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSecured(@HeaderParam("Authorization") String authorizationHeader) {
        return verifyAndRespond(authorizationHeader, "GET");
    }

    /**
     * Handles POST requests to the secured endpoint and verifies the authorization token.
     *
     * @param authorizationHeader The HTTP Authorization header.
     * @return A Response with status 200 on valid token, 401 if missing, or 403 if invalid.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response postSecured(@HeaderParam("Authorization") String authorizationHeader) {
        return verifyAndRespond(authorizationHeader, "POST");
    }

    /**
     * Verifies the authorization header token and builds the appropriate response.
     *
     * @param authorizationHeader The raw HTTP Authorization header value.
     * @param method              The HTTP request method name.
     * @return A Response object indicating authorization outcome.
     */
    private Response verifyAndRespond(String authorizationHeader, String method) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "unauthorized");
            err.put("message", "Missing Authorization header");
            return Response.status(Response.Status.UNAUTHORIZED).entity(err).build();
        }

        String token = authorizationHeader.trim();
        if (token.toLowerCase().startsWith("bearer ")) {
            token = token.substring(7).trim();
        }

        if (!DummyOauth2Resource.VALID_TOKEN.equals(token)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "access_denied");
            err.put("message", "Invalid access token");
            return Response.status(Response.Status.FORBIDDEN).entity(err).build();
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "success");
        r.put("message", "Access granted to secured endpoint");
        r.put("method", method);
        r.put("path", "/sample/secured");
        return Response.ok(r).build();
    }
}

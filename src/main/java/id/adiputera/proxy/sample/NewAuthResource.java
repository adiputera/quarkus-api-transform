package id.adiputera.proxy.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/auth/v2/token")
@ApplicationScoped
public class NewAuthResource {

    private final ObjectMapper mapper = new ObjectMapper();

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response token(@HeaderParam("X-API-Key") String apiKey,
                          @HeaderParam("Authorization") String authorization,
                          byte[] body) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("error", "missing_api_key");
            err.put("message", "X-API-Key header is required");
            return Response.status(Response.Status.UNAUTHORIZED).entity(err).build();
        }

        Map<String, Object> r = new LinkedHashMap<>();
        r.put("path", "/auth/v2/token");
        r.put("apiKey", apiKey);
        if (authorization != null) {
            r.put("authorization", authorization);
        }
        if (body != null && body.length > 0) {
            try {
                JsonNode node = mapper.readTree(body);
                r.put("body", node);
            } catch (IOException e) {
                r.put("body", new String(body));
            }
        }
        r.put("token", "issued-for-" + apiKey);
        return Response.ok(r).build();
    }
}

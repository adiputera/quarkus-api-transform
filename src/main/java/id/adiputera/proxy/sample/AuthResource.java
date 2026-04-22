package id.adiputera.proxy.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/auth/token")
@ApplicationScoped
public class AuthResource {

    private final ObjectMapper mapper = new ObjectMapper();

    @POST
    @Consumes(MediaType.WILDCARD)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> token(@QueryParam("user") String user, byte[] body,
                                     @Context UriInfo uriInfo) throws IOException {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("path", "/auth/token");
        r.put("user", user);
        r.put("query", uriInfo.getQueryParameters());
        if (body != null && body.length > 0) {
            try {
                JsonNode node = mapper.readTree(body);
                r.put("body", node);
            } catch (IOException e) {
                r.put("body", new String(body));
            }
        }
        return r;
    }
}

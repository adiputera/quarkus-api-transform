package id.adiputera.proxy.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/v2/products")
@ApplicationScoped
public class V2ProductResource {

    private final ObjectMapper mapper = new ObjectMapper();

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> create(byte[] body, @Context UriInfo uriInfo) throws IOException {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("path", "/v2/products");
        r.put("query", uriInfo.getQueryParameters());
        if (body != null && body.length > 0) {
            JsonNode node = mapper.readTree(body);
            r.put("body", node);
        }
        return r;
    }
}

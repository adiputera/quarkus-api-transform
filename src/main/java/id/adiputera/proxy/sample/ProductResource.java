package id.adiputera.proxy.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/products")
@ApplicationScoped
public class ProductResource {

    private final ObjectMapper mapper = new ObjectMapper();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> list(@Context UriInfo uriInfo) {
        return echo("/products", uriInfo, null, null);
    }

    @GET
    @Path("/search")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> search(@QueryParam("q") String q, @Context UriInfo uriInfo) {
        Map<String, Object> r = echo("/products/search", uriInfo, null, null);
        r.put("q", q);
        return r;
    }

    @GET
    @Path("/{code}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> getByCode(@PathParam("code") String code, @Context UriInfo uriInfo) {
        Map<String, Object> r = echo("/products/{code}", uriInfo, null, null);
        r.put("code", code);
        return r;
    }

    @POST
    @Path("/{code}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> updateByCode(@PathParam("code") String code, byte[] body,
                                            @Context UriInfo uriInfo) throws IOException {
        Map<String, Object> r = echo("/products/{code}", uriInfo, null, body);
        r.put("code", code);
        return r;
    }

    Map<String, Object> echo(String path, UriInfo uriInfo, String extraKey, byte[] body) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("path", path);
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

package id.adiputera.proxy.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
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

/**
 * Sample order controller for testing GET and POST order endpoints with query parameters.
 *
 * @author Yusuf F. Adiputera
 */
@Path("/orders")
@ApplicationScoped
public class OrderResource {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Lists orders for a given user ID query parameter.
     *
     * @param userId  The user ID from user_id query param.
     * @param uriInfo The URI info context.
     * @return A map echoing the order listing details.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> list(@QueryParam("user_id") String userId, @Context UriInfo uriInfo) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("method", "GET");
        r.put("path", "/orders");
        r.put("user_id", userId);
        r.put("query", uriInfo.getQueryParameters());
        return r;
    }

    /**
     * Creates an order for a given user ID query parameter and request body.
     *
     * @param userId  The user ID from user_id query param.
     * @param body    The request payload body.
     * @param uriInfo The URI info context.
     * @return A map echoing the order creation details.
     * @throws IOException if JSON parsing of the body fails.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> create(@QueryParam("user_id") String userId, byte[] body,
                                      @Context UriInfo uriInfo) throws IOException {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("method", "POST");
        r.put("path", "/orders");
        r.put("user_id", userId);
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

package id.adiputera.proxy.sample;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sample resource providing echo endpoints for testing HTTP methods and sub-path forwarding.
 *
 * @author Yusuf F. Adiputera
 */
@Path("/api/new-services")
@ApplicationScoped
public class NewServicesResource {

    /**
     * Echoes back details of a GET request.
     *
     * @param rest    The sub-path captured from the request URL.
     * @param uriInfo The URI info context.
     * @return A map containing request details.
     */
    @GET
    @Path("/{rest:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> get(@PathParam("rest") String rest, @Context UriInfo uriInfo) {
        return echo("GET", rest, uriInfo);
    }

    /**
     * Echoes back details of a POST request.
     *
     * @param rest    The sub-path captured from the request URL.
     * @param uriInfo The URI info context.
     * @return A map containing request details.
     */
    @POST
    @Path("/{rest:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> post(@PathParam("rest") String rest, @Context UriInfo uriInfo) {
        return echo("POST", rest, uriInfo);
    }

    /**
     * Echoes back details of a PUT request.
     *
     * @param rest    The sub-path captured from the request URL.
     * @param uriInfo The URI info context.
     * @return A map containing request details.
     */
    @PUT
    @Path("/{rest:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> put(@PathParam("rest") String rest, @Context UriInfo uriInfo) {
        return echo("PUT", rest, uriInfo);
    }

    /**
     * Echoes back details of a DELETE request.
     *
     * @param rest    The sub-path captured from the request URL.
     * @param uriInfo The URI info context.
     * @return A map containing request details.
     */
    @DELETE
    @Path("/{rest:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> delete(@PathParam("rest") String rest, @Context UriInfo uriInfo) {
        return echo("DELETE", rest, uriInfo);
    }

    /**
     * Formats the request attributes into a map structure.
     *
     * @param method  The HTTP method name.
     * @param rest    The sub-path string.
     * @param uriInfo The URI info context.
     * @return A map of attributes.
     */
    private static Map<String, Object> echo(String method, String rest, UriInfo uriInfo) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("method", method);
        r.put("path", "/api/new-services/" + rest);
        r.put("suffix", rest);
        r.put("query", uriInfo.getQueryParameters());
        return r;
    }
}

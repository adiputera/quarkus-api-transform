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

@Path("/api/new-services")
@ApplicationScoped
public class NewServicesResource {

    @GET
    @Path("/{rest:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> get(@PathParam("rest") String rest, @Context UriInfo uriInfo) {
        return echo("GET", rest, uriInfo);
    }

    @POST
    @Path("/{rest:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> post(@PathParam("rest") String rest, @Context UriInfo uriInfo) {
        return echo("POST", rest, uriInfo);
    }

    @PUT
    @Path("/{rest:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> put(@PathParam("rest") String rest, @Context UriInfo uriInfo) {
        return echo("PUT", rest, uriInfo);
    }

    @DELETE
    @Path("/{rest:.*}")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, Object> delete(@PathParam("rest") String rest, @Context UriInfo uriInfo) {
        return echo("DELETE", rest, uriInfo);
    }

    private static Map<String, Object> echo(String method, String rest, UriInfo uriInfo) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("method", method);
        r.put("path", "/api/new-services/" + rest);
        r.put("suffix", rest);
        r.put("query", uriInfo.getQueryParameters());
        return r;
    }
}

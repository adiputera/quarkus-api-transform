package id.adiputera.proxy.proxy;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("/{any:.*}")
@ApplicationScoped
public class ProxyResource {

    private final ProxyService proxyService;

    public ProxyResource(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @GET
    public Response get(@Context UriInfo uriInfo, @Context HttpHeaders headers) throws Exception {
        return proxyService.proxy("GET", uriInfo, headers, null);
    }

    @POST
    public Response post(@Context UriInfo uriInfo, @Context HttpHeaders headers, byte[] body) throws Exception {
        return proxyService.proxy("POST", uriInfo, headers, body);
    }

    @PUT
    public Response put(@Context UriInfo uriInfo, @Context HttpHeaders headers, byte[] body) throws Exception {
        return proxyService.proxy("PUT", uriInfo, headers, body);
    }

    @DELETE
    public Response delete(@Context UriInfo uriInfo, @Context HttpHeaders headers, byte[] body) throws Exception {
        return proxyService.proxy("DELETE", uriInfo, headers, body);
    }

    @PATCH
    public Response patch(@Context UriInfo uriInfo, @Context HttpHeaders headers, byte[] body) throws Exception {
        return proxyService.proxy("PATCH", uriInfo, headers, body);
    }

    @HEAD
    public Response head(@Context UriInfo uriInfo, @Context HttpHeaders headers) throws Exception {
        return proxyService.proxy("HEAD", uriInfo, headers, null);
    }

    @OPTIONS
    public Response options(@Context UriInfo uriInfo, @Context HttpHeaders headers, byte[] body) throws Exception {
        return proxyService.proxy("OPTIONS", uriInfo, headers, body);
    }
}

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

/**
 * Catch-all JAX-RS endpoint forwarding all HTTP methods and paths through the proxy engine.
 *
 * @author Yusuf F. Adiputera
 */
@Path("/{any:.*}")
@ApplicationScoped
public class ProxyResource {

    private final ProxyService proxyService;

    /**
     * Constructs a new ProxyResource with the core proxy service.
     *
     * @param proxyService The core proxy service.
     */
    public ProxyResource(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    /**
     * Proxies HTTP GET requests.
     *
     * @param uriInfo The request URI info.
     * @param headers The HTTP request headers.
     * @return The proxied response.
     * @throws Exception if proxy processing fails.
     */
    @GET
    public Response get(@Context UriInfo uriInfo, @Context HttpHeaders headers) throws Exception {
        return proxyService.proxy("GET", uriInfo, headers, null);
    }

    /**
     * Proxies HTTP POST requests.
     *
     * @param uriInfo The request URI info.
     * @param headers The HTTP request headers.
     * @param body    The request body bytes.
     * @return The proxied response.
     * @throws Exception if proxy processing fails.
     */
    @POST
    public Response post(@Context UriInfo uriInfo, @Context HttpHeaders headers, byte[] body) throws Exception {
        return proxyService.proxy("POST", uriInfo, headers, body);
    }

    /**
     * Proxies HTTP PUT requests.
     *
     * @param uriInfo The request URI info.
     * @param headers The HTTP request headers.
     * @param body    The request body bytes.
     * @return The proxied response.
     * @throws Exception if proxy processing fails.
     */
    @PUT
    public Response put(@Context UriInfo uriInfo, @Context HttpHeaders headers, byte[] body) throws Exception {
        return proxyService.proxy("PUT", uriInfo, headers, body);
    }

    /**
     * Proxies HTTP DELETE requests.
     *
     * @param uriInfo The request URI info.
     * @param headers The HTTP request headers.
     * @param body    The request body bytes.
     * @return The proxied response.
     * @throws Exception if proxy processing fails.
     */
    @DELETE
    public Response delete(@Context UriInfo uriInfo, @Context HttpHeaders headers, byte[] body) throws Exception {
        return proxyService.proxy("DELETE", uriInfo, headers, body);
    }

    /**
     * Proxies HTTP PATCH requests.
     *
     * @param uriInfo The request URI info.
     * @param headers The HTTP request headers.
     * @param body    The request body bytes.
     * @return The proxied response.
     * @throws Exception if proxy processing fails.
     */
    @PATCH
    public Response patch(@Context UriInfo uriInfo, @Context HttpHeaders headers, byte[] body) throws Exception {
        return proxyService.proxy("PATCH", uriInfo, headers, body);
    }

    /**
     * Proxies HTTP HEAD requests.
     *
     * @param uriInfo The request URI info.
     * @param headers The HTTP request headers.
     * @return The proxied response.
     * @throws Exception if proxy processing fails.
     */
    @HEAD
    public Response head(@Context UriInfo uriInfo, @Context HttpHeaders headers) throws Exception {
        return proxyService.proxy("HEAD", uriInfo, headers, null);
    }

    /**
     * Proxies HTTP OPTIONS requests.
     *
     * @param uriInfo The request URI info.
     * @param headers The HTTP request headers.
     * @param body    The request body bytes.
     * @return The proxied response.
     * @throws Exception if proxy processing fails.
     */
    @OPTIONS
    public Response options(@Context UriInfo uriInfo, @Context HttpHeaders headers, byte[] body) throws Exception {
        return proxyService.proxy("OPTIONS", uriInfo, headers, body);
    }
}

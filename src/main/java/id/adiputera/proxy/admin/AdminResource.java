package id.adiputera.proxy.admin;

import id.adiputera.proxy.config.ConfigProvider;
import id.adiputera.proxy.config.ReloadResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * REST endpoint for administrative actions such as configuration reloading.
 *
 * @author Yusuf F. Adiputera
 */
@Path("/admin/config/reload")
@ApplicationScoped
public class AdminResource {

    private final ConfigProvider configProvider;

    /**
     * Constructs a new AdminResource with the given configuration provider.
     *
     * @param configProvider The configuration provider.
     */
    public AdminResource(ConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    /**
     * Reloads active proxy route and backend configurations.
     *
     * @return A response containing the reload result details.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response reload() {
        ReloadResult result = configProvider.reload();
        return Response.ok(result).build();
    }
}

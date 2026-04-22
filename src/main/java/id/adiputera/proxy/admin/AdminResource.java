package id.adiputera.proxy.admin;

import id.adiputera.proxy.config.ConfigProvider;
import id.adiputera.proxy.config.ReloadResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/admin/config/reload")
@ApplicationScoped
public class AdminResource {

    private final ConfigProvider configProvider;

    public AdminResource(ConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response reload() {
        ReloadResult result = configProvider.reload();
        return Response.ok(result).build();
    }
}

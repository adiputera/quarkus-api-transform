package id.adiputera.proxy.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Provider
public class IllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {

    @Override
    public Response toResponse(IllegalStateException ex) {
        log.warn("Config reload rejected: {}", ex.getMessage());
        return Response.status(400)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("ConfigReloadError", ex.getMessage()))
                .build();
    }
}

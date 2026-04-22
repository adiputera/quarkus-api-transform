package id.adiputera.proxy.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception ex) {
        log.error("Unexpected proxy error", ex);
        return Response.status(502)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("InternalProxyError", ex.getMessage()))
                .build();
    }
}

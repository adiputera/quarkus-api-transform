package id.adiputera.proxy.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpTimeoutException;

@Slf4j
@Provider
public class HttpTimeoutExceptionMapper implements ExceptionMapper<HttpTimeoutException> {

    @Override
    public Response toResponse(HttpTimeoutException ex) {
        log.error("Backend request timed out: {}", ex.getMessage());
        return Response.status(504)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("GatewayTimeoutError", ex.getMessage()))
                .build();
    }
}

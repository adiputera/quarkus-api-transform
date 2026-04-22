package id.adiputera.proxy.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
@Provider
public class IOExceptionMapper implements ExceptionMapper<IOException> {

    @Override
    public Response toResponse(IOException ex) {
        log.error("Backend connection failed: {}", ex.getMessage());
        return Response.status(502)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("BadGatewayError", ex.getMessage()))
                .build();
    }
}

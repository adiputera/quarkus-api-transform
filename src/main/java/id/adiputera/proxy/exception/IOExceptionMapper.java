package id.adiputera.proxy.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Exception mapper converting {@link IOException} into a 502 Bad Gateway response.
 *
 * @author Yusuf F. Adiputera
 */
@Slf4j
@Provider
public class IOExceptionMapper implements ExceptionMapper<IOException> {

    /**
     * Maps an IO exception from upstream connectivity failures to a 502 Bad Gateway response.
     *
     * @param ex The IO exception.
     * @return A 502 HTTP response containing the error details.
     * @see ExceptionMapper#toResponse(Throwable)
     */
    @Override
    public Response toResponse(IOException ex) {
        log.error("Backend connection failed: {}", ex.getMessage());
        return Response.status(502)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("BadGatewayError", ex.getMessage()))
                .build();
    }
}

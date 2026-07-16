package id.adiputera.proxy.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

import java.net.http.HttpTimeoutException;

/**
 * Exception mapper converting {@link HttpTimeoutException} into a 504 Gateway Timeout response.
 *
 * @author Yusuf F. Adiputera
 */
@Slf4j
@Provider
public class HttpTimeoutExceptionMapper implements ExceptionMapper<HttpTimeoutException> {

    /**
     * Maps an HTTP timeout exception to a 504 Gateway Timeout response.
     *
     * @param ex The timeout exception.
     * @return A 504 HTTP response containing the error details.
     * @see ExceptionMapper#toResponse(Throwable)
     */
    @Override
    public Response toResponse(HttpTimeoutException ex) {
        log.error("Backend request timed out: {}", ex.getMessage());
        return Response.status(504)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("GatewayTimeoutError", ex.getMessage()))
                .build();
    }
}

package id.adiputera.proxy.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Exception mapper converting {@link IllegalStateException} into a 400 Bad Request response.
 *
 * @author Yusuf F. Adiputera
 */
@Slf4j
@Provider
public class IllegalStateExceptionMapper implements ExceptionMapper<IllegalStateException> {

    /**
     * Maps an illegal state exception to a 400 Bad Request response.
     *
     * @param ex The illegal state exception.
     * @return A 400 HTTP response containing the error details.
     * @see ExceptionMapper#toResponse(Throwable)
     */
    @Override
    public Response toResponse(IllegalStateException ex) {
        log.warn("Config reload rejected: {}", ex.getMessage());
        return Response.status(400)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("ConfigReloadError", ex.getMessage()))
                .build();
    }
}

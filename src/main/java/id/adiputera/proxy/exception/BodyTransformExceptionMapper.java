package id.adiputera.proxy.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Exception mapper converting {@link BodyTransformException} errors into structured HTTP JSON responses.
 *
 * @author Yusuf F. Adiputera
 */
@Slf4j
@Provider
public class BodyTransformExceptionMapper implements ExceptionMapper<BodyTransformException> {

    /**
     * Maps a body transformation exception to an HTTP response matching its status code.
     *
     * @param ex The body transformation exception.
     * @return The HTTP response.
     * @see ExceptionMapper#toResponse(Throwable)
     */
    @Override
    public Response toResponse(BodyTransformException ex) {
        log.warn("Body transform failed: {} ({})", ex.getMessage(), ex.getStatus());
        String type = ex.getStatus().getStatusCode() == 415 ? "UnsupportedMediaTypeError" : "BodyTransformError";
        return Response.status(ex.getStatus())
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse(type, ex.getMessage()))
                .build();
    }
}

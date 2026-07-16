package id.adiputera.proxy.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

/**
 * Exception mapper handling unexpected generic exceptions by returning a 502 Bad Gateway response.
 *
 * @author Yusuf F. Adiputera
 */
@Slf4j
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Exception> {

    /**
     * Maps an unexpected exception to an HTTP response.
     *
     * @param ex The unexpected exception.
     * @return A 502 HTTP response containing the error response payload.
     * @see ExceptionMapper#toResponse(Throwable)
     */
    @Override
    public Response toResponse(Exception ex) {
        log.error("Unexpected proxy error", ex);
        return Response.status(502)
                .type(MediaType.APPLICATION_JSON)
                .entity(new ErrorResponse("InternalProxyError", ex.getMessage()))
                .build();
    }
}

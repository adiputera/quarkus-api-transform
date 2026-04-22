package id.adiputera.proxy.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Provider
public class BodyTransformExceptionMapper implements ExceptionMapper<BodyTransformException> {

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

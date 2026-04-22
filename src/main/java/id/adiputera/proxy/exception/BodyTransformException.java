package id.adiputera.proxy.exception;

import jakarta.ws.rs.core.Response;

public class BodyTransformException extends RuntimeException {

    private final Response.Status status;

    public BodyTransformException(Response.Status status, String message) {
        super(message);
        this.status = status;
    }

    public BodyTransformException(Response.Status status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public Response.Status getStatus() {
        return status;
    }
}

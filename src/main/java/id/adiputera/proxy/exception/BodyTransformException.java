package id.adiputera.proxy.exception;

import jakarta.ws.rs.core.Response;

/**
 * Exception indicating a failure during body parameter or payload transformation.
 *
 * @author Yusuf F. Adiputera
 */
public class BodyTransformException extends RuntimeException {

    private final Response.Status status;

    /**
     * Constructs a new BodyTransformException with HTTP status and message.
     *
     * @param status  The associated HTTP status.
     * @param message The detail message.
     */
    public BodyTransformException(Response.Status status, String message) {
        super(message);
        this.status = status;
    }

    /**
     * Constructs a new BodyTransformException with HTTP status, message, and cause.
     *
     * @param status  The associated HTTP status.
     * @param message The detail message.
     * @param cause   The underlying cause.
     */
    public BodyTransformException(Response.Status status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    /**
     * Gets the HTTP status associated with this transformation failure.
     *
     * @return The HTTP status.
     */
    public Response.Status getStatus() {
        return status;
    }
}

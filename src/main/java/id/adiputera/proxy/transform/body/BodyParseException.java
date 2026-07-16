package id.adiputera.proxy.transform.body;

/**
 * Exception thrown when request body decoding or encoding encounters syntax or format errors.
 *
 * @author Yusuf F. Adiputera
 */
public class BodyParseException extends RuntimeException {
    /**
     * Constructs a new BodyParseException with the specified detail message and cause.
     *
     * @param message The detail message.
     * @param cause   The underlying parsing exception.
     */
    public BodyParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

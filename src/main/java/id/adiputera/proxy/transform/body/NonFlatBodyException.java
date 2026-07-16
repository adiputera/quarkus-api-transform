package id.adiputera.proxy.transform.body;

/**
 * Exception thrown when attempting flat form/key-value operations on a nested structure.
 *
 * @author Yusuf F. Adiputera
 */
public class NonFlatBodyException extends RuntimeException {
    /**
     * Constructs a new NonFlatBodyException with the specified detail message.
     *
     * @param message The detail message explaining the nested structure conflict.
     */
    public NonFlatBodyException(String message) {
        super(message);
    }
}

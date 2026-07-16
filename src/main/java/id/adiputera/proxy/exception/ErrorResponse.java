package id.adiputera.proxy.exception;

import lombok.Getter;

import java.util.List;

/**
 * Standard API error response wrapper containing a list of error details.
 *
 * @author Yusuf F. Adiputera
 */
@Getter
public class ErrorResponse {

    private final List<ErrorDetail> errors;

    /**
     * Constructs a single-error wrapper response.
     *
     * @param type    The error classification code or type.
     * @param message The descriptive error message.
     */
    public ErrorResponse(String type, String message) {
        this.errors = List.of(new ErrorDetail(type, message));
    }
}

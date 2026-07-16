package id.adiputera.proxy.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Individual error item containing a classification type and descriptive message.
 *
 * @author Yusuf F. Adiputera
 */
@Getter
@AllArgsConstructor
public class ErrorDetail {

    private String type;

    private String message;
}

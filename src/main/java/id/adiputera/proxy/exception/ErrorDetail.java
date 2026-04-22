package id.adiputera.proxy.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ErrorDetail {

    private String type;

    private String message;
}

package id.adiputera.proxy.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class ErrorResponse {

    private final List<ErrorDetail> errors;

    public ErrorResponse(String type, String message) {
        this.errors = List.of(new ErrorDetail(type, message));
    }
}

package co.id.mcs.dika.exception;

import lombok.Getter;

@Getter
public class AppException extends RuntimeException {
    private final Integer statusCode;

    public AppException(String message) {
        super(message);
        this.statusCode = 400;
    }

    public AppException(Integer statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }
}

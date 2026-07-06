package co.id.mcs.dika.exception;

import lombok.Getter;

@Getter
public class DataProcessingException extends RuntimeException {

    private final String serviceCode;
    private final String actionCode;

    public DataProcessingException(String serviceCode, String actionCode, Throwable cause) {
        super(cause);
        this.serviceCode = serviceCode;
        this.actionCode = actionCode;
    }

    public DataProcessingException(String serviceCode, String actionCode, String message) {
        super(message);
        this.serviceCode = serviceCode;
        this.actionCode = actionCode;
    }

    public DataProcessingException(String serviceCode, String actionCode) {
        super("Internal Server Error");
        this.serviceCode = serviceCode;
        this.actionCode = actionCode;
    }
}

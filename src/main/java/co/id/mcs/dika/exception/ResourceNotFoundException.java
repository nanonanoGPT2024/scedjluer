package co.id.mcs.dika.exception;

import lombok.Getter;

@Getter
public class ResourceNotFoundException extends RuntimeException {
    
    private final String serviceCode;
    private final String actionCode;
    private final String resourceId;

    public ResourceNotFoundException(String serviceCode, String actionCode, String resourceId) {
        super("Data with id : " + resourceId + " not found...");
        this.serviceCode = serviceCode;
        this.actionCode = actionCode;
        this.resourceId = resourceId;
    }
}

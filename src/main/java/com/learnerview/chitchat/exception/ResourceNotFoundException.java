package com.learnerview.chitchat.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a requested resource is not found.
 * Maps to HTTP 404.
 */
public class ResourceNotFoundException extends BaseException {
    
    private static final int HTTP_STATUS = HttpStatus.NOT_FOUND.value();

    public ResourceNotFoundException(String message) {
        super(message, "RESOURCE_NOT_FOUND", HTTP_STATUS);
    }
    
    public ResourceNotFoundException(String errorCode, String message) {
        super(message, errorCode, HTTP_STATUS);
    }

    public ResourceNotFoundException(String resourceType, String identifier, boolean typed) {
        super(String.format("%s with identifier '%s' not found", resourceType, identifier), 
              "RESOURCE_NOT_FOUND", HTTP_STATUS);
    }
}

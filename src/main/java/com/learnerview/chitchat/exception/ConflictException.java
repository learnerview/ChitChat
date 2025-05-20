package com.learnerview.chitchat.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when a resource conflict occurs (e.g., duplicate entry).
 * Maps to HTTP 409.
 */
public class ConflictException extends BaseException {
    
    private static final String ERROR_CODE = "CONFLICT";
    private static final int HTTP_STATUS = HttpStatus.CONFLICT.value();

    public ConflictException(String message) {
        super(message, ERROR_CODE, HTTP_STATUS);
    }

    public ConflictException(String resourceType, String identifier) {
        super(String.format("%s with identifier '%s' already exists", resourceType, identifier), 
              ERROR_CODE, HTTP_STATUS);
    }
}

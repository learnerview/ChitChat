package com.learnerview.chitchat.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when user lacks permission for the requested action.
 * Maps to HTTP 403.
 */
public class ForbiddenException extends BaseException {
    
    private static final int HTTP_STATUS = HttpStatus.FORBIDDEN.value();

    public ForbiddenException(String message) {
        super(message, "FORBIDDEN", HTTP_STATUS);
    }

    public ForbiddenException(String errorCode, String message) {
        super(message, errorCode, HTTP_STATUS);
    }

    public ForbiddenException() {
        super("You do not have permission to perform this action", "FORBIDDEN", HTTP_STATUS);
    }
}

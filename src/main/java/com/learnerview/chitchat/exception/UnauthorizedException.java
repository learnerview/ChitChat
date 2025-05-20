package com.learnerview.chitchat.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when authentication is required but not provided.
 * Maps to HTTP 401.
 */
public class UnauthorizedException extends BaseException {
    
    private static final String ERROR_CODE = "UNAUTHORIZED";
    private static final int HTTP_STATUS = HttpStatus.UNAUTHORIZED.value();

    public UnauthorizedException(String message) {
        super(message, ERROR_CODE, HTTP_STATUS);
    }

    public UnauthorizedException() {
        super("Authentication required", ERROR_CODE, HTTP_STATUS);
    }
}

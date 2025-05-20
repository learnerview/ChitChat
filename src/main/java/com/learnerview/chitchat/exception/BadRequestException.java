package com.learnerview.chitchat.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown for invalid request data.
 * Maps to HTTP 400.
 */
public class BadRequestException extends BaseException {
    
    private static final int HTTP_STATUS = HttpStatus.BAD_REQUEST.value();

    public BadRequestException(String message) {
        super(message, "BAD_REQUEST", HTTP_STATUS);
    }
    
    public BadRequestException(String errorCode, String message) {
        super(message, errorCode, HTTP_STATUS);
    }
}

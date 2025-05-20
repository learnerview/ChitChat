package com.learnerview.chitchat.exception;

import lombok.Getter;

/**
 * Base exception class for all application-specific exceptions.
 * Provides consistent error code and message structure.
 */
@Getter
public abstract class BaseException extends RuntimeException {
    
    private final String errorCode;
    private final int httpStatus;

    protected BaseException(String message, String errorCode, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    protected BaseException(String message, String errorCode, int httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
}

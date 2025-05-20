package com.learnerview.chitchat.exception;

import org.springframework.http.HttpStatus;

public class DatabaseException extends BaseException {
    public DatabaseException(String message) {
        super("DATABASE_ERROR", message, HttpStatus.SERVICE_UNAVAILABLE.value());
    }
    
    public DatabaseException(String code, String message) {
        super(code, message, HttpStatus.SERVICE_UNAVAILABLE.value());
    }
}

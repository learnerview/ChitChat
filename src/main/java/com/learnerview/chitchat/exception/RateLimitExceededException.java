package com.learnerview.chitchat.exception;

import org.springframework.http.HttpStatus;

public class RateLimitExceededException extends BaseException {
    public RateLimitExceededException(String message) {
        super("RATE_LIMIT_EXCEEDED", message, HttpStatus.TOO_MANY_REQUESTS.value());
    }
    
    public RateLimitExceededException(String code, String message) {
        super(code, message, HttpStatus.TOO_MANY_REQUESTS.value());
    }
}

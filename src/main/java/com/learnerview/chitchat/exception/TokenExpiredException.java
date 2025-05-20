package com.learnerview.chitchat.exception;

import org.springframework.http.HttpStatus;

public class TokenExpiredException extends BaseException {
    public TokenExpiredException(String message) {
        super("TOKEN_EXPIRED", message, HttpStatus.UNAUTHORIZED.value());
    }
    
    public TokenExpiredException(String code, String message) {
        super(code, message, HttpStatus.UNAUTHORIZED.value());
    }
}

package com.learnerview.chitchat.exception;

import org.springframework.http.HttpStatus;

public class WebSocketException extends BaseException {
    public WebSocketException(String message) {
        super("WEBSOCKET_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
    
    public WebSocketException(String code, String message) {
        super(code, message, HttpStatus.INTERNAL_SERVER_ERROR.value());
    }
}

package com.learnerview.chitchat.exception;

public class MessageException extends RuntimeException {
    private final String errorCode;
    
    public MessageException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public MessageException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}

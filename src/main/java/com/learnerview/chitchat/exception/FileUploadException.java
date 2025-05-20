package com.learnerview.chitchat.exception;

public class FileUploadException extends RuntimeException {
    private final String errorCode;
    
    public FileUploadException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public FileUploadException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
}

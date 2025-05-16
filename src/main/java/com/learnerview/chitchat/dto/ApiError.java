package com.learnerview.chitchat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Standardized error response structure for API errors.
 * Provides consistent error format across all endpoints.
 */
@Data
@Builder
public class ApiError {
    
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    private int status;
    private String errorCode;
    private String message;
    private String path;
    
    public static ApiError of(int status, String errorCode, String message, String path) {
        return ApiError.builder()
                .timestamp(LocalDateTime.now())
                .status(status)
                .errorCode(errorCode)
                .message(message)
                .path(path)
                .build();
    }
}

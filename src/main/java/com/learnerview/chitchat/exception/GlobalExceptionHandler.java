package com.learnerview.chitchat.exception;

import com.learnerview.chitchat.dto.ApiError;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * Global exception handler for REST API endpoints.
 * Converts exceptions to standardized ApiError responses.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ====== Custom Application Exceptions ======

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ApiError> handleBaseException(BaseException ex, HttpServletRequest request) {
        log.warn("Application exception: {} - {}", ex.getErrorCode(), ex.getMessage());
        
        ApiError error = ApiError.of(
            ex.getHttpStatus(),
            ex.getErrorCode(),
            ex.getMessage(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(ex.getHttpStatus()).body(error);
    }

    // ====== Spring Validation Exceptions ======

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationException(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        
        log.warn("Validation failed: {}", message);
        
        ApiError error = ApiError.of(
            HttpStatus.BAD_REQUEST.value(),
            "VALIDATION_FAILED",
            message.isEmpty() ? "Validation failed" : message,
            request.getRequestURI()
        );
        
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        
        String message = String.format("Required parameter '%s' is missing", ex.getParameterName());
        log.warn(message);
        
        ApiError error = ApiError.of(
            HttpStatus.BAD_REQUEST.value(),
            "MISSING_PARAMETER",
            message,
            request.getRequestURI()
        );
        
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        
        String message = String.format("Parameter '%s' has invalid type", ex.getName());
        log.warn(message);
        
        ApiError error = ApiError.of(
            HttpStatus.BAD_REQUEST.value(),
            "INVALID_PARAMETER_TYPE",
            message,
            request.getRequestURI()
        );
        
        return ResponseEntity.badRequest().body(error);
    }

    // ====== Security Exceptions ======

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthenticationException(
            AuthenticationException ex, HttpServletRequest request) {
        
        log.warn("Authentication failed: {}", ex.getMessage());
        
        ApiError error = ApiError.of(
            HttpStatus.UNAUTHORIZED.value(),
            "AUTHENTICATION_FAILED",
            "Authentication required",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(
            AccessDeniedException ex, HttpServletRequest request) {
        
        log.warn("Access denied: {}", ex.getMessage());
        
        ApiError error = ApiError.of(
            HttpStatus.FORBIDDEN.value(),
            "ACCESS_DENIED",
            "You do not have permission to access this resource",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    // ====== Not Found ======

    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            NoHandlerFoundException ex, HttpServletRequest request) {
        
        log.warn("No handler found: {}", ex.getRequestURL());
        
        ApiError error = ApiError.of(
            HttpStatus.NOT_FOUND.value(),
            "ENDPOINT_NOT_FOUND",
            "The requested endpoint does not exist",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    // ====== JWT Token Errors ======

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ApiError> handleExpiredJwt(
            ExpiredJwtException ex, HttpServletRequest request) {
        
        log.warn("JWT token expired: {}", ex.getMessage());
        
        ApiError error = ApiError.of(
            HttpStatus.UNAUTHORIZED.value(),
            "TOKEN_EXPIRED",
            "Authentication token has expired",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ApiError> handleJwtException(
            JwtException ex, HttpServletRequest request) {
        
        log.warn("JWT error: {}", ex.getMessage());
        
        ApiError error = ApiError.of(
            HttpStatus.UNAUTHORIZED.value(),
            "INVALID_TOKEN",
            "Invalid authentication token",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(error);
    }

    // ====== Database Errors ======

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiError> handleDataAccessException(
            DataAccessException ex, HttpServletRequest request) {
        
        log.error("Database error: {}", ex.getMessage());
        
        ApiError error = ApiError.of(
            HttpStatus.SERVICE_UNAVAILABLE.value(),
            "DATABASE_ERROR",
            "Database service is temporarily unavailable",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    // ====== Catch-All ======

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenericException(
            Exception ex, HttpServletRequest request) {
        
        log.error("Unexpected error: ", ex);
        
        ApiError error = ApiError.of(
            HttpStatus.INTERNAL_SERVER_ERROR.value(),
            "INTERNAL_ERROR",
            "An unexpected error occurred. Please try again later.",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

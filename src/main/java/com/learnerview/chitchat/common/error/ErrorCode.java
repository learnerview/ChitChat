package com.learnerview.chitchat.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
    MISSING_TENANT(HttpStatus.BAD_REQUEST, "Missing X-Tenant-Id header"),
    INVALID_CURSOR(HttpStatus.BAD_REQUEST, "Invalid pagination cursor"),

    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),

    FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    TENANT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "You are not a member of this workspace"),
    INSUFFICIENT_ROLE(HttpStatus.FORBIDDEN, "Your workspace role does not allow this action"),
    CONVERSATION_ACCESS_DENIED(HttpStatus.FORBIDDEN, "You are not a member of this conversation"),
    INSUFFICIENT_CONVERSATION_ROLE(HttpStatus.FORBIDDEN, "Only the conversation owner can perform this action"),

    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),
    CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Conversation not found"),
    MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Message not found"),
    WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "Workspace not found"),

    CONFLICT(HttpStatus.CONFLICT, "Conflict"),
    USERNAME_TAKEN(HttpStatus.CONFLICT, "Username already taken"),
    SLUG_TAKEN(HttpStatus.CONFLICT, "Workspace slug already exists"),
    ALREADY_MEMBER(HttpStatus.CONFLICT, "User is already an active member"),
    CLIENT_MESSAGE_ID_CONFLICT(HttpStatus.CONFLICT, "clientMessageId was already used for a different message"),

    MESSAGE_TOO_LONG(HttpStatus.PAYLOAD_TOO_LARGE, "Message content exceeds the maximum length"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}

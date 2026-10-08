package com.learnerview.chitchat.message.dto;

/**
 * Optional read cursor. When null the server marks everything up to the
 * conversation's latest sequence as read.
 */
public record MarkReadRequest(Long sequence) {
}

package com.learnerview.chitchat.realtime.dto;

/**
 * Resume payload sent to {@code /app/conversations/{id}/resume} after
 * reconnecting. {@code afterSequence} is the last sequence the client
 * processed; the server replays everything newer on the caller's private
 * queue. Absent/null means the client has no history position and only wants
 * the current latest sequence reported.
 */
public record ResumeRequest(Long afterSequence) {
}

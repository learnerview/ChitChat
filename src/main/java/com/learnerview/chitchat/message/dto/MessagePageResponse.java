package com.learnerview.chitchat.message.dto;

import java.util.List;

/**
 * Cursor page over the per-conversation sequence space.
 * {@code nextCursor} is passed back as {@code before} (or {@code after}) to keep paging.
 */
public record MessagePageResponse(
        List<MessageResponse> messages,
        boolean hasMore,
        Long nextCursor
) {
}

package com.learnerview.chitchat.realtime;

public enum RealtimeEventType {
    MESSAGE_CREATED,
    MESSAGE_EDITED,
    MESSAGE_DELETED,
    READ_UPDATED,
    MEMBER_ADDED,
    MEMBER_REMOVED,
    MEMBER_ROLE_CHANGED,
    CONVERSATION_DELETED,
    /** Terminal marker of a resume batch; carries the caller's latest known position. */
    SYNC_COMPLETE
}

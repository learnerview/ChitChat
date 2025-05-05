package com.learnerview.chitchat.entities;

public enum ConversationType {
    /** One-on-one private chat */
    DM,
    /** Private group chat (invite-only, like WhatsApp groups) */
    PRIVATE_GROUP,
    /** Public group chat (discoverable and joinable) */
    PUBLIC_GROUP
}

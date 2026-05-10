package com.learnerview.chitchat.dto;

public record TenantInfo(
        String id,
        String name,
        String slug,
        String role
) {}

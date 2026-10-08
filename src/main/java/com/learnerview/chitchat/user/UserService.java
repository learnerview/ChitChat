package com.learnerview.chitchat.user;

import java.util.List;
import java.util.Optional;

public interface UserService {

    User register(String username, String displayName, String rawPassword);

    Optional<User> findById(String userId);

    Optional<User> findByUsername(String username);

    /** Batch profile lookup restricted to users sharing the caller's workspace. */
    java.util.List<User> findVisibleUsers(java.util.Collection<String> userIds);

    List<User> searchUsers(String query);

    User updateProfile(String userId, String displayName);

    void deleteAccount(String userId);

    void changePassword(String userId, String currentPassword, String newPassword);
}

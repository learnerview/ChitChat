package com.learnerview.chitchat.service;

import com.learnerview.chitchat.entities.User;

import java.util.List;
import java.util.Optional;

public interface UserService {
    User register(User user);

    Optional<User> findByUsername(String username);

    List<User> searchUsers(String query);

    User updateProfile(String username, String displayName);

    void deleteAccount(String username);

    void changePassword(String username, String currentPassword, String newPassword);
}
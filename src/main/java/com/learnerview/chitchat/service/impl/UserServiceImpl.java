package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.entities.User;
import com.learnerview.chitchat.repositories.UserRepository;
import com.learnerview.chitchat.service.UserService;
import com.learnerview.chitchat.tenant.TenantContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public User register(User user) {
        String tenantId = TenantContext.getRequiredTenantId();
        if (userRepository.existsByTenantIdAndUsername(tenantId, user.getUsername())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username already exists");
        }

        user.setTenantId(tenantId);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setCreatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByTenantIdAndUsername(TenantContext.getRequiredTenantId(), username);
    }

    @Override
    public List<User> searchUsers(String query) {
        String tenantId = TenantContext.getRequiredTenantId();
        return userRepository.findByTenantIdAndUsernameContainingIgnoreCaseOrTenantIdAndDisplayNameContainingIgnoreCase(
                tenantId,
                query,
                tenantId,
                query
        );
    }

    @Override
    public User updateProfile(String username, String displayName) {
        User user = findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (displayName != null) user.setDisplayName(displayName);

        return userRepository.save(user);
    }

    @Override
    public void deleteAccount(String username) {
        User user = findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        userRepository.delete(user);
    }

    @Override
    public void changePassword(String username, String currentPassword, String newPassword) {
        User user = findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be between 8 and 100 characters");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }
}

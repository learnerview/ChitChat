package com.learnerview.chitchat.user;

import com.learnerview.chitchat.common.error.ApiException;
import com.learnerview.chitchat.common.error.ErrorCode;
import com.learnerview.chitchat.conversation.ConversationMemberRepository;
import com.learnerview.chitchat.tenant.TenantMember;
import com.learnerview.chitchat.tenant.TenantMemberRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TenantMemberRepository tenantMemberRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final com.learnerview.chitchat.authorization.AuthorizationService authorizationService;

    public UserServiceImpl(UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           TenantMemberRepository tenantMemberRepository,
                           ConversationMemberRepository conversationMemberRepository,
                           com.learnerview.chitchat.authorization.AuthorizationService authorizationService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tenantMemberRepository = tenantMemberRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.authorizationService = authorizationService;
    }

    @Override
    public User register(String username, String displayName, String rawPassword) {
        if (userRepository.existsByUsername(username)) {
            throw new ApiException(ErrorCode.USERNAME_TAKEN);
        }

        User user = User.builder()
                .username(username)
                .displayName(displayName)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .status(UserStatus.ACTIVE)
                .passwordChangedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        return userRepository.save(user);
    }

    @Override
    public Optional<User> findById(String userId) {
        return userRepository.findById(userId);
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Override
    public List<User> searchUsers(String query) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < 2) {
            return List.of();
        }
        // Users are a global collection; results are scoped to the caller's
        // workspace so accounts cannot be enumerated across tenants.
        String tenantId = com.learnerview.chitchat.common.tenancy.TenantContext.getRequiredTenantId();
        authorizationService.requireTenantMember(tenantId);
        Set<String> visibleUserIds = tenantMemberRepository
                .findByTenantIdAndRemovedAtIsNull(tenantId).stream()
                .map(TenantMember::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        return userRepository
                .findByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(trimmed, trimmed)
                .stream()
                .filter(user -> visibleUserIds.contains(user.getId()))
                .limit(25)
                .toList();
    }


    @Override
    public List<User> findVisibleUsers(java.util.Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return List.of();
        }
        String tenantId = com.learnerview.chitchat.common.tenancy.TenantContext.getRequiredTenantId();
        authorizationService.requireTenantMember(tenantId);
        Set<String> visibleUserIds = tenantMemberRepository
                .findByTenantIdAndRemovedAtIsNull(tenantId).stream()
                .map(TenantMember::getUserId)
                .collect(java.util.stream.Collectors.toSet());
        List<String> ids = userIds.stream()
                .filter(visibleUserIds::contains)
                .distinct()
                .limit(100)
                .toList();
        return userRepository.findAllById(ids);
    }

    @Override
    public User updateProfile(String userId, String displayName) {
        User user = findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName.trim());
        }
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    @Override
    public void deleteAccount(String userId) {
        User user = findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        // A sole owner must transfer ownership first; otherwise the workspace
        // would be permanently unmanageable.
        List<TenantMember> memberships = tenantMemberRepository.findByUserIdAndRemovedAtIsNull(userId);
        for (TenantMember membership : memberships) {
            if (TenantMember.Role.OWNER.name().equals(membership.getRole())
                    && tenantMemberRepository.countByTenantIdAndRoleAndRemovedAtIsNull(
                            membership.getTenantId(), TenantMember.Role.OWNER.name()) <= 1) {
                throw new ApiException(ErrorCode.BAD_REQUEST,
                        "Transfer ownership of workspace " + membership.getTenantId()
                                + " before deleting your account");
            }
        }

        // Deactivate all memberships so nothing keeps serving the deleted account.
        LocalDateTime now = LocalDateTime.now();
        for (TenantMember membership : memberships) {
            membership.setRemovedAt(now);
            tenantMemberRepository.save(membership);
            conversationMemberRepository
                    .findByTenantIdAndUserIdAndLeftAtIsNull(membership.getTenantId(), userId)
                    .forEach(conversationMember -> {
                        conversationMember.setLeftAt(now);
                        conversationMemberRepository.save(conversationMember);
                    });
        }

        userRepository.delete(user);
    }

    @Override
    public void changePassword(String userId, String currentPassword, String newPassword) {
        User user = findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Current password is incorrect");
        }
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 100) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "New password must be between 8 and 100 characters");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);
    }
}

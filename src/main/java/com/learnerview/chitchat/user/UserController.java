package com.learnerview.chitchat.user;

import com.learnerview.chitchat.authorization.AuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuthorizationService authorizationService;

    public UserController(UserService userService, AuthorizationService authorizationService) {
        this.userService = userService;
        this.authorizationService = authorizationService;
    }

    public record UpdateProfileRequest(
            @NotBlank @Size(max = 80) String displayName
    ) {
    }

    @GetMapping("/search")
    public List<UserProfileResponse> searchUsers(@RequestParam String query) {
        return userService.searchUsers(query).stream().map(UserProfileResponse::from).toList();
    }

    @GetMapping("/profiles")
    public List<UserProfileResponse> getProfiles(@RequestParam List<String> ids) {
        return userService.findVisibleUsers(ids).stream().map(UserProfileResponse::from).toList();
    }

    @PutMapping("/profile")
    public UserProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return UserProfileResponse.from(
                userService.updateProfile(authorizationService.currentUserId(), request.displayName()));
    }

    @DeleteMapping("/me")
    public void deleteAccount() {
        userService.deleteAccount(authorizationService.currentUserId());
    }

    @PostMapping("/password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(
                authorizationService.currentUserId(),
                request.getCurrentPassword(),
                request.getNewPassword());
    }
}

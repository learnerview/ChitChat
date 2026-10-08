package com.learnerview.chitchat.auth;

import com.learnerview.chitchat.user.User;
import com.learnerview.chitchat.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Username-based lookup used only by the login AuthenticationManager.
 * After login the JWT subject (and thus all identity) is the userId.
 */
@Service
public class MongoUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public MongoUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return org.springframework.security.core.userdetails.User
                .withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .disabled(user.getStatus() != null
                        && com.learnerview.chitchat.user.UserStatus.DISABLED == user.getStatus())
                .roles("USER")
                .build();
    }
}

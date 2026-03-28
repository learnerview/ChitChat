package com.learnerview.chitchat.service.impl;

import com.learnerview.chitchat.entities.User;
import com.learnerview.chitchat.repositories.UserRepository;
import com.learnerview.chitchat.tenant.TenantContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class MongoUserDetailsService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String tenantId = TenantContext.getRequiredTenantId();
        User user = userRepository.findByTenantIdAndUsername(tenantId, username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        
        return org.springframework.security.core.userdetails.User.withUsername(user.getUsername())
            .password(user.getPassword())
            .roles("USER")
            .build();
    }
}

package com.coralshop.auth;

import com.coralshop.user.UserRepository;
import org.springframework.security.core.Authentication;

public record AuthenticatedUserResponse(String username, String email, String role) {

    public static AuthenticatedUserResponse from(Authentication authentication, UserRepository users) {
        String email = authentication.getName();
        String username = users.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found"))
                .getUsername();
        String role = authentication.getAuthorities().iterator().next().getAuthority();
        return new AuthenticatedUserResponse(username, email, role);
    }
}

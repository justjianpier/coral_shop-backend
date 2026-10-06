package com.coralshop.auth;

import org.springframework.security.core.Authentication;

public record AuthenticatedUserResponse(String email, String role) {

    public static AuthenticatedUserResponse from(Authentication authentication) {
        String role = authentication.getAuthorities().iterator().next().getAuthority();
        return new AuthenticatedUserResponse(authentication.getName(), role);
    }
}

package com.example.expensetracker.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CurrentUserService {

    public String getUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String userId = jwt.getClaimAsString("userId");
            if (StringUtils.hasText(userId)) {
                return userId;
            }
            return jwt.getSubject();
        }

        return authentication.getName();
    }
}

package com.example.expensetracker.security;

import com.example.expensetracker.demo.security.DemoPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CurrentUserService {

    private static final int MAX_FIRST_NAME_CODE_POINTS = 50;

    public String getUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Jwt jwt) {
            String oid = jwt.getClaimAsString("oid");
            if (StringUtils.hasText(oid)) {
                return oid;
            }

            String userId = jwt.getClaimAsString("userId");
            if (StringUtils.hasText(userId)) {
                return userId;
            }

            return jwt.getSubject();
        }

        if (principal instanceof DemoPrincipal demoPrincipal) {
            return demoPrincipal.persistenceOwnerId();
        }

        return authentication.getName();
    }

    public String getFirstName(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new IllegalArgumentException("Authenticated user is required.");
        }
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        return sanitizeFirstName(jwt.getClaimAsString("given_name"));
    }

    private String sanitizeFirstName(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        StringBuilder sanitized = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                sanitized.append(' ');
            } else if (Character.getType(codePoint) != Character.CONTROL) {
                sanitized.appendCodePoint(codePoint);
            }
        });

        String normalized = sanitized.toString().trim().replaceAll(" +", " ");
        if (normalized.isBlank()) {
            return null;
        }
        int codePoints = normalized.codePointCount(0, normalized.length());
        if (codePoints <= MAX_FIRST_NAME_CODE_POINTS) {
            return normalized;
        }
        int end = normalized.offsetByCodePoints(0, MAX_FIRST_NAME_CODE_POINTS);
        return normalized.substring(0, end).trim();
    }
}

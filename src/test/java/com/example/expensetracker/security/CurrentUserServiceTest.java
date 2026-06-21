package com.example.expensetracker.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentUserServiceTest {

    private final CurrentUserService currentUserService = new CurrentUserService();

    @Test
    void getUserId_prefersOidForJwtPrincipal() {
        Jwt jwt = jwt(Map.of(
                "oid", "entra-object-id",
                "userId", "legacy-user-id",
                "sub", "subject-id"));

        String userId = currentUserService.getUserId(authenticatedJwt(jwt));

        assertThat(userId).isEqualTo("entra-object-id");
    }

    @Test
    void getUserId_fallsBackToUserIdWhenOidIsMissing() {
        Jwt jwt = jwt(Map.of(
                "userId", "legacy-user-id",
                "sub", "subject-id"));

        String userId = currentUserService.getUserId(authenticatedJwt(jwt));

        assertThat(userId).isEqualTo("legacy-user-id");
    }

    @Test
    void getUserId_fallsBackToSubjectWhenOidAndUserIdAreMissing() {
        Jwt jwt = jwt(Map.of("sub", "subject-id"));

        String userId = currentUserService.getUserId(authenticatedJwt(jwt));

        assertThat(userId).isEqualTo("subject-id");
    }

    @Test
    void getUserId_usesAuthenticationNameForNonJwtPrincipal() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("dev-user", null);
        authentication.setAuthenticated(true);

        String userId = currentUserService.getUserId(authentication);

        assertThat(userId).isEqualTo("dev-user");
    }

    @Test
    void getUserId_rejectsMissingAuthentication() {
        assertThatThrownBy(() -> currentUserService.getUserId(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Authenticated user is required.");
    }

    @Test
    void getUserId_rejectsUnauthenticatedPrincipal() {
        TestingAuthenticationToken authentication = new TestingAuthenticationToken("dev-user", null);
        authentication.setAuthenticated(false);

        assertThatThrownBy(() -> currentUserService.getUserId(authentication))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Authenticated user is required.");
    }

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(300),
                Map.of("alg", "none"),
                claims);
    }

    private static Authentication authenticatedJwt(Jwt jwt) {
        return new JwtAuthenticationToken(jwt, Collections.emptyList());
    }
}

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

    @Test
    void getFirstName_sanitizesGivenName() {
        Jwt jwt = jwt(Map.of(
                "sub", "subject-id",
                "given_name", "  Ju" + Character.toString(0) + "an \n Dela  "));

        String firstName = currentUserService.getFirstName(authenticatedJwt(jwt));

        assertThat(firstName).isEqualTo("Juan Dela");
    }

    @Test
    void getFirstName_limitsResultToFiftyUnicodeCodePoints() {
        String givenName = "Á".repeat(49) + "😀extra";
        Jwt jwt = jwt(Map.of("sub", "subject-id", "given_name", givenName));

        String firstName = currentUserService.getFirstName(authenticatedJwt(jwt));

        assertThat(firstName.codePointCount(0, firstName.length())).isEqualTo(50);
        assertThat(firstName).endsWith("😀");
    }

    @Test
    void getFirstName_doesNotInferNameFromOtherClaims() {
        Jwt jwt = jwt(Map.of(
                "sub", "subject-id",
                "name", "Juan Dela Cruz",
                "preferred_username", "juan@example.test"));

        assertThat(currentUserService.getFirstName(authenticatedJwt(jwt))).isNull();
    }

    @Test
    void getFirstName_returnsNullForBlankOrNonJwtName() {
        Jwt blank = jwt(Map.of(
                "sub", "subject-id",
                "given_name", " \n" + Character.toString(0) + " "));
        TestingAuthenticationToken nonJwt = new TestingAuthenticationToken("dev-user", null);
        nonJwt.setAuthenticated(true);

        assertThat(currentUserService.getFirstName(authenticatedJwt(blank))).isNull();
        assertThat(currentUserService.getFirstName(nonJwt)).isNull();
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

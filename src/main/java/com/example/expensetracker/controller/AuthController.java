package com.example.expensetracker.controller;

import com.example.expensetracker.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Profile("dev")
@Tag(name = "Authentication", description = "Mock login endpoint for generating JWTs")
public class AuthController {

    private final JwtTokenProvider tokenProvider;

    @PostMapping("/login")
    @Operation(summary = "Mock Login", description = "Generates a JWT token for a given dummy user ID")
    public ResponseEntity<LoginResponse> authenticateUser(@RequestBody LoginRequest loginRequest) {
        String token = tokenProvider.generateToken(loginRequest.getUserId());
        return ResponseEntity.ok(new LoginResponse(token));
    }

    @Data
    static class LoginRequest {
        private String userId;
    }

    @Data
    static class LoginResponse {
        private String accessToken;
        private String tokenType = "Bearer";

        public LoginResponse(String accessToken) {
            this.accessToken = accessToken;
        }
    }
}

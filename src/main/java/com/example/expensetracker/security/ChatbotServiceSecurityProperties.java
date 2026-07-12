package com.example.expensetracker.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("chatbot.service")
public record ChatbotServiceSecurityProperties(
    String issuer,
    String audience,
    String jwkSetUri,
    String publicKeyLocation,
    String requiredRole
) {
}

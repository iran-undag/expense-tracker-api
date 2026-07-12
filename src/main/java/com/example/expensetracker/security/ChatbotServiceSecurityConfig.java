package com.example.expensetracker.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.env.Environment;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableConfigurationProperties(ChatbotServiceSecurityProperties.class)
public class ChatbotServiceSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain chatbotToolSecurityChain(
        HttpSecurity http,
        ChatbotServiceSecurityProperties properties,
        ResourceLoader resourceLoader,
        Environment environment
    ) throws Exception {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> roles(jwt.getClaimAsStringList("roles")));
        String role = StringUtils.hasText(properties.requiredRole())
            ? properties.requiredRole() : "CHATBOT_TOOL_EXECUTOR";

        return http
            .securityMatcher("/internal/chat-tools/**")
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().hasRole(role))
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                .decoder(chatbotDecoder(properties, resourceLoader, environment))
                .jwtAuthenticationConverter(converter)))
            .build();
    }

    private JwtDecoder chatbotDecoder(
        ChatbotServiceSecurityProperties properties,
        ResourceLoader resourceLoader,
        Environment environment
    ) {
        boolean production = java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (production && StringUtils.hasText(properties.publicKeyLocation())) {
            throw new IllegalStateException("Local chatbot public-key authentication is forbidden in production");
        }
        if (StringUtils.hasText(properties.publicKeyLocation())
            && StringUtils.hasText(properties.issuer())
            && StringUtils.hasText(properties.audience())) {
            try (var input = resourceLoader.getResource(properties.publicKeyLocation()).getInputStream()) {
                var publicKey = RsaKeyConverters.x509().convert(input);
                NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
                decoder.setJwtValidator(validators(properties));
                return decoder;
            } catch (Exception exception) {
                throw new IllegalStateException("Unable to load chatbot service public key", exception);
            }
        }
        if (!StringUtils.hasText(properties.issuer())
            || !StringUtils.hasText(properties.audience())
            || !StringUtils.hasText(properties.jwkSetUri())) {
            return token -> { throw new JwtException("Chatbot service authentication is not configured"); };
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        decoder.setJwtValidator(validators(properties));
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> validators(ChatbotServiceSecurityProperties properties) {
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains(properties.audience())
            ? OAuth2TokenValidatorResult.success()
            : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid audience", null));
        return new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(properties.issuer()), audience);
    }

    private Collection<GrantedAuthority> roles(List<String> roles) {
        if (roles == null) {
            return List.of();
        }
        return roles.stream()
            .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toUnmodifiableList());
    }
}

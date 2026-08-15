package com.example.expensetracker.security;

import com.example.expensetracker.config.CorrelationIdFilter;
import com.example.expensetracker.demo.security.DemoBearerTokenResolver;
import com.example.expensetracker.demo.security.DemoTokenAuthenticationFilter;
import com.example.expensetracker.demo.security.DemoTokenDigester;
import com.example.expensetracker.persistence.AuthenticatedRealmFilter;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableWebSecurity
@Profile("prod")
@SecurityScheme(
        name = "Bearer Authentication",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer"
)
public class ProdSecurityConfig {

    @Bean
    public SecurityFilterChain prodSecurityFilterChain(
            HttpSecurity http,
            CorrelationIdFilter correlationIdFilter,
            @Qualifier("demoJdbcTemplate") JdbcTemplate demoJdbcTemplate,
            DemoTokenDigester demoTokenDigester) throws Exception {
        DemoBearerTokenResolver demoBearerTokenResolver = new DemoBearerTokenResolver();
        DemoTokenAuthenticationFilter demoTokenAuthenticationFilter =
            new DemoTokenAuthenticationFilter(demoJdbcTemplate, demoTokenDigester);
        AuthenticatedRealmFilter authenticatedRealmFilter = new AuthenticatedRealmFilter();

        http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**",
                    "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers(HttpMethod.POST,
                    "/api/demo/sessions", "/api/demo/sessions/renew").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(demoTokenAuthenticationFilter, BearerTokenAuthenticationFilter.class)
            .addFilterAfter(authenticatedRealmFilter, BearerTokenAuthenticationFilter.class)
            .oauth2ResourceServer(oauth2 -> oauth2
                .bearerTokenResolver(demoBearerTokenResolver)
                .jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    DemoTokenDigester demoTokenDigester(@Value("${demo.token-hmac-key}") String rawKey) {
        return new DemoTokenDigester(rawKey);
    }

}

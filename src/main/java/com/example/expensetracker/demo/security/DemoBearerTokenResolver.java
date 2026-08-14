package com.example.expensetracker.demo.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;

public class DemoBearerTokenResolver implements BearerTokenResolver {

    public static final String TOKEN_PREFIX = "dmo_";

    private final BearerTokenResolver delegate = new DefaultBearerTokenResolver();

    @Override
    public String resolve(HttpServletRequest request) {
        String token = delegate.resolve(request);
        return token != null && token.startsWith(TOKEN_PREFIX) ? null : token;
    }
}

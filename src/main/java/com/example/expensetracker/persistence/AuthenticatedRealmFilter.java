package com.example.expensetracker.persistence;

import com.example.expensetracker.demo.security.DemoPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

public class AuthenticatedRealmFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        DataRealm realm = authenticatedRealm();
        if (realm == null) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean ownsScope = DataRealmContext.current().isEmpty();
        DataRealmContext.set(realm);
        try {
            filterChain.doFilter(request, response);
        } finally {
            if (ownsScope) {
                DataRealmContext.clear();
            }
        }
    }

    private DataRealm authenticatedRealm() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof DemoPrincipal) {
            return DataRealm.DEMO;
        }
        if (authentication.getPrincipal() instanceof Jwt) {
            return DataRealm.PRIMARY;
        }
        return null;
    }
}

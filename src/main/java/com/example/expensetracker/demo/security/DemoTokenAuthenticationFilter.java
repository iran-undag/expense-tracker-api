package com.example.expensetracker.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.web.filter.OncePerRequestFilter;

public class DemoTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String ACTIVE_TOKEN_QUERY = """
        SELECT s.id, s.shared_account_id, s.persistence_owner_id, s.expires_at
        FROM demo_access_token t
        JOIN demo_session s ON s.id = t.demo_session_id
        WHERE t.token_digest = ?
          AND t.expires_at > SYSDATETIMEOFFSET()
          AND s.expires_at > SYSDATETIMEOFFSET()
          AND s.status = 'ACTIVE'
        """;

    private final DefaultBearerTokenResolver bearerTokenResolver = new DefaultBearerTokenResolver();
    private final JdbcTemplate demoJdbc;
    private final DemoTokenDigester digester;

    public DemoTokenAuthenticationFilter(JdbcTemplate demoJdbc, DemoTokenDigester digester) {
        this.demoJdbc = demoJdbc;
        this.digester = digester;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String token;
        try {
            token = bearerTokenResolver.resolve(request);
        } catch (OAuth2AuthenticationException exception) {
            filterChain.doFilter(request, response);
            return;
        }
        if (token == null || !token.startsWith(DemoBearerTokenResolver.TOKEN_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        List<DemoPrincipal> principals = demoJdbc.query(ACTIVE_TOKEN_QUERY, new DemoPrincipalRowMapper(), digester.digest(token));
        if (principals.isEmpty()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return;
        }

        SecurityContext previousContext = SecurityContextHolder.getContext();
        SecurityContext demoContext = SecurityContextHolder.createEmptyContext();
        demoContext.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
            principals.get(0),
            null,
            List.of()
        ));
        SecurityContextHolder.setContext(demoContext);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.setContext(previousContext);
        }
    }

    private static final class DemoPrincipalRowMapper implements RowMapper<DemoPrincipal> {
        @Override
        public DemoPrincipal mapRow(ResultSet resultSet, int rowNum) throws SQLException {
            return new DemoPrincipal(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("shared_account_id"),
                resultSet.getString("persistence_owner_id"),
                resultSet.getObject("expires_at", OffsetDateTime.class)
            );
        }
    }
}

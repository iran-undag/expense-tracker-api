package com.example.expensetracker.demo.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.expensetracker.demo.security.DemoPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class DemoSessionHeadersAdviceTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void successfulDemoResponseIncludesCurrentQuotaMetadata() {
        UUID sessionId = UUID.randomUUID();
        OffsetDateTime expiresAt = OffsetDateTime.of(2026, 8, 15, 4, 0, 0, 0, ZoneOffset.UTC);
        DemoQuotaService quotaService = mock(DemoQuotaService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DemoQuotaService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(quotaService);
        when(quotaService.current(sessionId))
            .thenReturn(new DemoQuotaService.QuotaSnapshot(20, 7, expiresAt));
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new DemoPrincipal(sessionId, "shared", "demo:" + sessionId, expiresAt),
                null,
                List.of()
            )
        );
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        ServletServerHttpResponse serverResponse = new ServletServerHttpResponse(servletResponse);

        new DemoSessionHeadersAdvice(provider).beforeBodyWrite(
            null, null, null, null, null, serverResponse);

        assertThat(serverResponse.getHeaders().getFirst(DemoSessionHeadersAdvice.ACTIONS_LIMIT)).isEqualTo("20");
        assertThat(serverResponse.getHeaders().getFirst(DemoSessionHeadersAdvice.ACTIONS_REMAINING)).isEqualTo("7");
        assertThat(serverResponse.getHeaders().getFirst(DemoSessionHeadersAdvice.SESSION_EXPIRES_AT))
            .isEqualTo(expiresAt.toString());
    }

    @Test
    void errorResponseDoesNotQueryOrEmitQuotaMetadata() {
        DemoQuotaService quotaService = mock(DemoQuotaService.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DemoQuotaService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(quotaService);
        OffsetDateTime expiresAt = OffsetDateTime.now();
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new DemoPrincipal(UUID.randomUUID(), "shared", "demo:owner", expiresAt),
                null,
                List.of()
            )
        );
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();
        servletResponse.setStatus(429);

        new DemoSessionHeadersAdvice(provider).beforeBodyWrite(
            null, null, null, null, null, new ServletServerHttpResponse(servletResponse));

        assertThat(servletResponse.getHeaderNames()).isEmpty();
        verifyNoInteractions(quotaService);
    }
}

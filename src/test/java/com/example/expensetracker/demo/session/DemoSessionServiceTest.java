package com.example.expensetracker.demo.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.expensetracker.demo.security.DemoTokenDigester;
import com.example.expensetracker.demo.seed.DemoSeedRefresher;
import com.example.expensetracker.config.DemoMetrics;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class DemoSessionServiceTest {

    @Test
    void resumeRejectsSessionThatExpiresBeforeAccessTokenPersistence() {
        OffsetDateTime expiry = OffsetDateTime.of(2026, 8, 14, 12, 0, 0, 0, ZoneOffset.UTC);
        DemoSession session = DemoSession.builder()
            .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
            .sharedAccountId("demo-shared-account")
            .persistenceOwnerId("demo:11111111-1111-1111-1111-111111111111")
            .status("ACTIVE")
            .createdAt(expiry.minusHours(6))
            .expiresAt(expiry)
            .usedActions(0)
            .reservedActions(0)
            .resumeTokenDigest("digest")
            .build();
        DemoSessionRepository sessionRepository = mock(DemoSessionRepository.class);
        DemoAccessTokenRepository accessTokenRepository = mock(DemoAccessTokenRepository.class);
        DemoSessionRateLimiter rateLimiter = mock(DemoSessionRateLimiter.class);
        DemoTokenDigester digester = mock(DemoTokenDigester.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DemoSeedRefresher> seedRefresherProvider = mock(ObjectProvider.class);
        DemoMetrics metrics = mock(DemoMetrics.class);
        DemoSessionService service = new DemoSessionService(
            sessionRepository,
            accessTokenRepository,
            rateLimiter,
            digester,
            seedRefresherProvider,
            metrics
        );
        when(digester.digest("resume-token")).thenReturn("digest");
        when(sessionRepository.findByResumeDigest("digest")).thenReturn(Optional.of(session));
        when(sessionRepository.findActiveByResumeDigest("digest")).thenReturn(Optional.of(session));
        when(sessionRepository.lockActiveSession(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.databaseNow()).thenReturn(expiry);

        assertThatThrownBy(() -> service.createOrResume("resume-token", "203.0.113.8"))
            .isInstanceOfSatisfying(DemoSessionException.class,
                exception -> assertThat(exception.code()).isEqualTo("DEMO_SESSION_EXPIRED"));
        verify(sessionRepository, times(2)).deleteExpiredData();
        verifyNoInteractions(accessTokenRepository);
    }
}

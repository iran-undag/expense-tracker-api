package com.example.expensetracker.demo.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.expensetracker.config.DemoMetrics;
import com.example.expensetracker.demo.security.DemoTokenDigester;
import com.example.expensetracker.demo.seed.DemoSeedRefresher;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.beans.factory.ObjectProvider;

class DemoSessionServiceTest {

    private static final OffsetDateTime NOW =
        OffsetDateTime.of(2026, 8, 15, 8, 0, 0, 0, ZoneOffset.UTC);

    private DemoSessionRepository sessionRepository;
    private DemoAccessTokenRepository accessTokenRepository;
    private DemoTokenDigester digester;
    private DemoMetrics metrics;
    private DemoSessionService service;

    @BeforeEach
    void setUp() {
        sessionRepository = mock(DemoSessionRepository.class);
        accessTokenRepository = mock(DemoAccessTokenRepository.class);
        digester = mock(DemoTokenDigester.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<DemoSeedRefresher> seedRefresherProvider = mock(ObjectProvider.class);
        metrics = mock(DemoMetrics.class);
        service = new DemoSessionService(
            sessionRepository,
            accessTokenRepository,
            digester,
            seedRefresherProvider,
            metrics
        );
    }

    @Test
    void resumeRejectsSessionThatExpiresBeforeAccessTokenPersistence() {
        DemoSession session = activeSession(NOW, 0, 0);
        when(digester.digest("resume-token")).thenReturn("digest");
        when(sessionRepository.findByResumeDigest("digest")).thenReturn(Optional.of(session));
        when(sessionRepository.findActiveByResumeDigest("digest")).thenReturn(Optional.of(session));
        when(sessionRepository.lockActiveSession(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.databaseNow()).thenReturn(NOW);

        assertThatThrownBy(() -> service.createOrResume("resume-token"))
            .isInstanceOfSatisfying(DemoSessionException.class,
                exception -> assertThat(exception.code()).isEqualTo("DEMO_SESSION_EXPIRED"));
        verify(sessionRepository, never()).deleteExpiredData();
        verifyNoInteractions(accessTokenRepository);
    }

    @Test
    void rejectsHourlyAdmissionBeforeCheckingActiveCapacity() {
        when(sessionRepository.databaseNow()).thenReturn(NOW);
        when(sessionRepository.hourlyAdmissionCount(NOW)).thenReturn(4);
        when(sessionRepository.dailyAdmissionCount(NOW)).thenReturn(7);
        when(sessionRepository.oldestHourlyAdmission(NOW))
            .thenReturn(Optional.of(NOW.minusMinutes(59).minusNanos(500_000_000)));

        assertThatThrownBy(() -> service.createOrResume(null))
            .isInstanceOfSatisfying(DemoSessionException.class, exception -> {
                assertThat(exception.code()).isEqualTo("DEMO_CAPACITY_REACHED");
                assertThat(exception.retryAfterSeconds()).isEqualTo(60L);
            });

        verify(sessionRepository, never()).activeSessionCount();
        verify(sessionRepository, never()).recordAdmission(any());
    }

    @Test
    void usesLongerDailyRetryAfterWhenBothAdmissionWindowsAreFull() {
        when(sessionRepository.databaseNow()).thenReturn(NOW);
        when(sessionRepository.hourlyAdmissionCount(NOW)).thenReturn(4);
        when(sessionRepository.dailyAdmissionCount(NOW)).thenReturn(12);
        when(sessionRepository.oldestHourlyAdmission(NOW))
            .thenReturn(Optional.of(NOW.minusMinutes(59).minusNanos(250_000_000)));
        when(sessionRepository.oldestDailyAdmission(NOW))
            .thenReturn(Optional.of(NOW.minusHours(23).minusNanos(250_000_000)));

        assertThatThrownBy(() -> service.createOrResume(null))
            .isInstanceOfSatisfying(DemoSessionException.class,
                exception -> assertThat(exception.retryAfterSeconds()).isEqualTo(3_600L));
    }

    @Test
    void recordsAdmissionOnlyAfterSavingANewSession() {
        when(sessionRepository.databaseNow()).thenReturn(NOW, NOW);
        when(sessionRepository.hourlyAdmissionCount(NOW)).thenReturn(3);
        when(sessionRepository.dailyAdmissionCount(NOW)).thenReturn(11);
        when(sessionRepository.activeSessionCount()).thenReturn(0);
        when(digester.generateAccessToken()).thenReturn("resume-token", "access-token");
        when(digester.digest(any())).thenReturn("digest");

        service.createOrResume(null);

        InOrder writes = inOrder(sessionRepository);
        writes.verify(sessionRepository).save(any(DemoSession.class));
        writes.verify(sessionRepository).recordAdmission(NOW);
    }

    @Test
    void renewRejectsMissingCookieWithoutCreatingSession() {
        assertThatThrownBy(() -> service.renew(null))
            .isInstanceOfSatisfying(DemoSessionException.class,
                exception -> assertThat(exception.code()).isEqualTo("DEMO_SESSION_EXPIRED"));

        verify(sessionRepository, never()).save(any());
        verify(sessionRepository, never()).recordAdmission(any());
        verifyNoInteractions(accessTokenRepository);
    }

    @Test
    void renewRejectsCookieWithoutActiveSessionWithoutCreatingSession() {
        when(digester.digest("invalid-token")).thenReturn("digest");
        when(sessionRepository.findActiveByResumeDigest("digest")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.renew("invalid-token"))
            .isInstanceOfSatisfying(DemoSessionException.class,
                exception -> assertThat(exception.code()).isEqualTo("DEMO_SESSION_EXPIRED"));

        verify(sessionRepository, never()).save(any());
        verify(sessionRepository, never()).recordAdmission(any());
        verifyNoInteractions(accessTokenRepository);
    }

    @Test
    void renewIssuesTokenForSameActiveSessionWithoutChangingExpiryOrQuota() {
        DemoSession session = activeSession(NOW.plusMinutes(40), 3, 2);
        when(digester.digest("resume-token")).thenReturn("resume-digest");
        when(digester.digest("access-token")).thenReturn("access-digest");
        when(digester.generateAccessToken()).thenReturn("access-token");
        when(sessionRepository.findActiveByResumeDigest("resume-digest"))
            .thenReturn(Optional.of(session));
        when(sessionRepository.lockActiveSession(session.getId())).thenReturn(Optional.of(session));
        when(sessionRepository.databaseNow()).thenReturn(NOW, NOW);
        when(sessionRepository.activeSessionCount()).thenReturn(1);

        DemoSessionService.SessionGrant grant = service.renew("resume-token");

        assertThat(grant.response().sessionExpiresAt()).isEqualTo(session.getExpiresAt());
        assertThat(grant.response().actionLimit()).isEqualTo(DemoSessionService.ACTION_LIMIT);
        assertThat(grant.response().usedActions()).isEqualTo(3);
        assertThat(grant.response().remainingActions())
            .isEqualTo(DemoSessionService.ACTION_LIMIT - 5);
        verify(sessionRepository, never()).recordAdmission(any());
        verify(sessionRepository, never()).save(any());
        verify(accessTokenRepository).save(any(DemoAccessToken.class));
    }

    private static DemoSession activeSession(
        OffsetDateTime expiry,
        int usedActions,
        int reservedActions
    ) {
        UUID sessionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        return DemoSession.builder()
            .id(sessionId)
            .sharedAccountId("demo-shared-account")
            .persistenceOwnerId("demo:" + sessionId)
            .status("ACTIVE")
            .createdAt(expiry.minusHours(1))
            .expiresAt(expiry)
            .usedActions(usedActions)
            .reservedActions(reservedActions)
            .resumeTokenDigest("digest")
            .build();
    }
}

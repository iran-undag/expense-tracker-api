package com.example.expensetracker.demo.session;

import com.example.expensetracker.demo.security.DemoTokenDigester;
import com.example.expensetracker.demo.seed.DemoSeedRefresher;
import com.example.expensetracker.config.DemoMetrics;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("prod")
public class DemoSessionService {

    public static final int ACTION_LIMIT = 20;
    private static final int SESSION_HOURS = 6;
    private static final int ACCESS_TOKEN_MINUTES = 15;
    private static final int MAX_ACTIVE_SESSIONS = 2;
    private static final String SHARED_ACCOUNT_ID = "demo-shared-account";
    private static final ZoneId DEMO_ZONE = ZoneId.of("Asia/Manila");

    private final DemoSessionRepository sessionRepository;
    private final DemoAccessTokenRepository accessTokenRepository;
    private final DemoSessionRateLimiter rateLimiter;
    private final DemoTokenDigester tokenDigester;
    private final ObjectProvider<DemoSeedRefresher> seedRefresherProvider;
    private final DemoMetrics metrics;

    public DemoSessionService(
        DemoSessionRepository sessionRepository,
        DemoAccessTokenRepository accessTokenRepository,
        DemoSessionRateLimiter rateLimiter,
        DemoTokenDigester tokenDigester,
        ObjectProvider<DemoSeedRefresher> seedRefresherProvider,
        DemoMetrics metrics
    ) {
        this.sessionRepository = sessionRepository;
        this.accessTokenRepository = accessTokenRepository;
        this.rateLimiter = rateLimiter;
        this.tokenDigester = tokenDigester;
        this.seedRefresherProvider = seedRefresherProvider;
        this.metrics = metrics;
    }

    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        noRollbackFor = DemoSessionException.class
    )
    public SessionGrant createOrResume(String rawResumeCookie, String remoteAddress) {
        Optional<DemoSession> cookieSession = findCookieSession(rawResumeCookie);
        metrics.cleanedSessions(sessionRepository.deleteExpiredData());

        if (cookieSession.isPresent()
            && sessionRepository.findActiveByResumeDigest(tokenDigester.digest(rawResumeCookie)).isEmpty()) {
            throw DemoSessionException.sessionExpired();
        }

        Optional<DemoSession> activeSession = findActiveCookieSession(rawResumeCookie);
        if (activeSession.isPresent()) {
            DemoSession lockedSession = sessionRepository.lockActiveSession(activeSession.get().getId())
                .orElseThrow(DemoSessionException::sessionExpired);
            sessionRepository.reclaimExpiredReservations(
                lockedSession, sessionRepository.databaseNow());
            metrics.sessionResumed();
            metrics.activeSessions(sessionRepository.activeSessionCount());
            return issueAccessToken(lockedSession, rawResumeCookie);
        }

        rateLimiter.checkAndRecord(remoteAddress);
        OffsetDateTime now = sessionRepository.databaseNow();
        YearMonth anchorMonth = YearMonth.from(now.atZoneSameInstant(DEMO_ZONE));
        sessionRepository.ensureAdmissionRow(anchorMonth.atDay(1), now);
        sessionRepository.lockAdmissionRow();

        int activeCount = sessionRepository.activeSessionCount();
        if (activeCount >= MAX_ACTIVE_SESSIONS) {
            throw DemoSessionException.capacityReached(capacityRetryAfter(now));
        }
        if (activeCount == 0) {
            seedRefresherProvider.ifAvailable(refresher -> refresher.refreshIfStale(anchorMonth));
        }

        UUID sessionId = UUID.randomUUID();
        String resumeToken = tokenDigester.generateAccessToken();
        OffsetDateTime expiresAt = now.plusHours(SESSION_HOURS);
        DemoSession session = DemoSession.builder()
            .id(sessionId)
            .sharedAccountId(SHARED_ACCOUNT_ID)
            .persistenceOwnerId("demo:" + sessionId)
            .status("ACTIVE")
            .createdAt(now)
            .expiresAt(expiresAt)
            .usedActions(0)
            .reservedActions(0)
            .resumeTokenDigest(tokenDigester.digest(resumeToken))
            .build();
        sessionRepository.save(session);
        metrics.sessionCreated();
        metrics.activeSessions(activeCount + 1);

        return issueAccessToken(session, resumeToken);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void logout(UUID sessionId) {
        sessionRepository.lockActiveSession(sessionId)
            .orElseThrow(DemoSessionException::sessionExpired);
        sessionRepository.deleteOwnedData(sessionId);
        sessionRepository.markLoggedOut(
            sessionId,
            tokenDigester.digest("logged-out:" + UUID.randomUUID())
        );
        metrics.sessionLoggedOut();
        metrics.activeSessions(sessionRepository.activeSessionCount());
    }

    private Optional<DemoSession> findCookieSession(String rawResumeCookie) {
        if (rawResumeCookie == null || rawResumeCookie.isBlank()) {
            return Optional.empty();
        }
        return sessionRepository.findByResumeDigest(tokenDigester.digest(rawResumeCookie));
    }

    private Optional<DemoSession> findActiveCookieSession(String rawResumeCookie) {
        if (rawResumeCookie == null || rawResumeCookie.isBlank()) {
            return Optional.empty();
        }
        return sessionRepository.findActiveByResumeDigest(tokenDigester.digest(rawResumeCookie));
    }

    private SessionGrant issueAccessToken(DemoSession session, String resumeToken) {
        OffsetDateTime now = sessionRepository.databaseNow();
        if (!now.isBefore(session.getExpiresAt())) {
            metrics.cleanedSessions(sessionRepository.deleteExpiredData());
            throw DemoSessionException.sessionExpired();
        }
        OffsetDateTime accessTokenExpiresAt = min(now.plusMinutes(ACCESS_TOKEN_MINUTES), session.getExpiresAt());
        String accessToken = tokenDigester.generateAccessToken();
        accessTokenRepository.save(DemoAccessToken.builder()
            .demoSessionId(session.getId())
            .tokenDigest(tokenDigester.digest(accessToken))
            .createdAt(now)
            .expiresAt(accessTokenExpiresAt)
            .build());

        int remainingActions = Math.max(0,
            ACTION_LIMIT - session.getUsedActions() - session.getReservedActions());
        return new SessionGrant(
            new DemoSessionResponse(
                accessToken,
                accessTokenExpiresAt,
                session.getExpiresAt(),
                ACTION_LIMIT,
                session.getUsedActions(),
                remainingActions
            ),
            resumeToken,
            Math.max(0, Duration.between(now, session.getExpiresAt()).getSeconds())
        );
    }

    private long capacityRetryAfter(OffsetDateTime now) {
        return sessionRepository.earliestActiveExpiry()
            .map(expiry -> Math.max(1, Duration.between(now, expiry).getSeconds()))
            .orElse(1L);
    }

    private static OffsetDateTime min(OffsetDateTime first, OffsetDateTime second) {
        return first.isBefore(second) ? first : second;
    }

    public record SessionGrant(
        DemoSessionResponse response,
        String resumeToken,
        long resumeCookieMaxAgeSeconds
    ) {
    }
}

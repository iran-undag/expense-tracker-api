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

    public static final int ACTION_LIMIT = 10;
    private static final int SESSION_HOURS = 1;
    private static final int ACCESS_TOKEN_MINUTES = 15;
    private static final int MAX_ACTIVE_SESSIONS = 2;
    private static final int HOURLY_ADMISSION_LIMIT = 4;
    private static final int DAILY_ADMISSION_LIMIT = 12;
    private static final String SHARED_ACCOUNT_ID = "demo-shared-account";
    private static final ZoneId DEMO_ZONE = ZoneId.of("Asia/Manila");

    private final DemoSessionRepository sessionRepository;
    private final DemoAccessTokenRepository accessTokenRepository;
    private final DemoTokenDigester tokenDigester;
    private final ObjectProvider<DemoSeedRefresher> seedRefresherProvider;
    private final DemoMetrics metrics;

    public DemoSessionService(
        DemoSessionRepository sessionRepository,
        DemoAccessTokenRepository accessTokenRepository,
        DemoTokenDigester tokenDigester,
        ObjectProvider<DemoSeedRefresher> seedRefresherProvider,
        DemoMetrics metrics
    ) {
        this.sessionRepository = sessionRepository;
        this.accessTokenRepository = accessTokenRepository;
        this.tokenDigester = tokenDigester;
        this.seedRefresherProvider = seedRefresherProvider;
        this.metrics = metrics;
    }

    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        noRollbackFor = DemoSessionException.class
    )
    public SessionGrant createOrResume(String rawResumeCookie) {
        Optional<DemoSession> cookieSession = findCookieSession(rawResumeCookie);

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

        OffsetDateTime now = sessionRepository.databaseNow();
        YearMonth anchorMonth = YearMonth.from(now.atZoneSameInstant(DEMO_ZONE));
        sessionRepository.ensureAdmissionRow(anchorMonth.atDay(1), now);
        sessionRepository.lockAdmissionRow();
        sessionRepository.deleteAdmissionsOutsideWindow(now);
        enforceRollingAdmission(now);

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
        sessionRepository.recordAdmission(now);
        metrics.sessionCreated();
        metrics.activeSessions(activeCount + 1);

        return issueAccessToken(session, resumeToken);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public SessionGrant renew(String rawResumeCookie) {
        if (rawResumeCookie == null || rawResumeCookie.isBlank()) {
            throw DemoSessionException.sessionExpired();
        }
        String resumeDigest = tokenDigester.digest(rawResumeCookie);
        DemoSession activeSession = sessionRepository.findActiveByResumeDigest(resumeDigest)
            .orElseThrow(DemoSessionException::sessionExpired);
        DemoSession lockedSession = sessionRepository.lockActiveSession(activeSession.getId())
            .orElseThrow(DemoSessionException::sessionExpired);
        sessionRepository.reclaimExpiredReservations(
            lockedSession, sessionRepository.databaseNow());
        metrics.sessionResumed();
        metrics.activeSessions(sessionRepository.activeSessionCount());
        return issueAccessToken(lockedSession, rawResumeCookie);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void logout(UUID sessionId) {
        sessionRepository.lockActiveSession(sessionId)
            .orElseThrow(DemoSessionException::sessionExpired);
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
            .map(expiry -> retryAfterSeconds(now, expiry))
            .orElse(1L);
    }

    private void enforceRollingAdmission(OffsetDateTime now) {
        OffsetDateTime retryAt = null;
        if (sessionRepository.hourlyAdmissionCount(now) >= HOURLY_ADMISSION_LIMIT) {
            retryAt = sessionRepository.oldestHourlyAdmission(now)
                .map(oldest -> oldest.plusHours(1))
                .orElse(now.plusSeconds(1));
        }
        if (sessionRepository.dailyAdmissionCount(now) >= DAILY_ADMISSION_LIMIT) {
            OffsetDateTime dailyRetryAt = sessionRepository.oldestDailyAdmission(now)
                .map(oldest -> oldest.plusHours(24))
                .orElse(now.plusSeconds(1));
            if (retryAt == null || dailyRetryAt.isAfter(retryAt)) {
                retryAt = dailyRetryAt;
            }
        }
        if (retryAt != null) {
            throw DemoSessionException.capacityReached(retryAfterSeconds(now, retryAt));
        }
    }

    private static long retryAfterSeconds(OffsetDateTime now, OffsetDateTime retryAt) {
        Duration remaining = Duration.between(now, retryAt);
        long seconds = remaining.getSeconds();
        if (remaining.getNano() > 0) {
            seconds++;
        }
        return Math.max(1, seconds);
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

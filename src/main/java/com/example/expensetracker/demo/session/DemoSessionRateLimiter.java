package com.example.expensetracker.demo.session;

import com.example.expensetracker.demo.security.DemoTokenDigester;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("prod")
public class DemoSessionRateLimiter {

    private static final int PER_IP_LIMIT = 5;
    private static final int GLOBAL_LIMIT = 30;

    private final DemoSessionRepository sessionRepository;
    private final DemoTokenDigester digester;

    public DemoSessionRateLimiter(DemoSessionRepository sessionRepository, DemoTokenDigester digester) {
        this.sessionRepository = sessionRepository;
        this.digester = digester;
    }

    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        noRollbackFor = DemoSessionException.class
    )
    public void checkAndRecord(String remoteAddress) {
        String ipDigest = digester.digest(normalize(remoteAddress));
        sessionRepository.deleteOldAttempts();

        if (sessionRepository.attemptCountForIp(ipDigest) >= PER_IP_LIMIT) {
            throw DemoSessionException.capacityReached(retryAfter(ipDigest, false));
        }
        if (sessionRepository.globalAttemptCount() >= GLOBAL_LIMIT) {
            throw DemoSessionException.capacityReached(retryAfter(ipDigest, true));
        }
        sessionRepository.recordAttempt(ipDigest);
    }

    static String normalize(String remoteAddress) {
        String candidate = remoteAddress == null ? "" : remoteAddress.trim();
        if (candidate.isEmpty()) {
            return "unknown";
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress().toLowerCase(Locale.ROOT);
        } catch (UnknownHostException exception) {
            return candidate.toLowerCase(Locale.ROOT);
        }
    }

    private long retryAfter(String ipDigest, boolean global) {
        OffsetDateTime now = sessionRepository.databaseNow();
        return sessionRepository.oldestAttempt(ipDigest, global)
            .map(oldest -> Math.max(1, Duration.between(now, oldest.plusHours(1)).getSeconds()))
            .orElse(1L);
    }
}

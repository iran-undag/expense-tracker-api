package com.example.expensetracker.demo.quota;

import com.example.expensetracker.demo.session.DemoSession;
import com.example.expensetracker.demo.session.DemoSessionException;
import com.example.expensetracker.demo.session.DemoSessionRepository;
import com.example.expensetracker.demo.session.DemoSessionService;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoQuotaService {

    public static final int ACTION_LIMIT = DemoSessionService.ACTION_LIMIT;

    private final Optional<DemoSessionRepository> sessionRepository;

    public DemoQuotaService(Optional<DemoSessionRepository> sessionRepository) {
        this.sessionRepository = sessionRepository;
    }

    DemoSession lockForMutation(UUID sessionId, int cost) {
        validateCost(cost);
        DemoSession session = repository().lockActiveSession(sessionId)
            .orElseThrow(DemoSessionException::sessionExpired);
        repository().reclaimExpiredReservations(session, repository().databaseNow());
        if (totalActions(session) + cost > ACTION_LIMIT) {
            throw DemoSessionException.quotaExhausted();
        }
        return session;
    }

    void recordUsed(DemoSession session, int cost) {
        session.setUsedActions(session.getUsedActions() + cost);
    }

    public boolean tryConsume(UUID sessionId, int cost) {
        if (sessionRepository.isEmpty()) {
            return true;
        }
        DemoSession session;
        try {
            session = lockForMutation(sessionId, cost);
        } catch (DemoSessionException exception) {
            if ("DEMO_QUOTA_EXHAUSTED".equals(exception.code())) {
                return false;
            }
            throw exception;
        }
        recordUsed(session, cost);
        return true;
    }

    @Transactional(readOnly = true)
    public QuotaSnapshot current(UUID sessionId) {
        DemoSession session = repository().findActiveSession(sessionId)
            .orElseThrow(DemoSessionException::sessionExpired);
        return new QuotaSnapshot(
            ACTION_LIMIT,
            Math.max(0, ACTION_LIMIT - totalActions(session)),
            session.getExpiresAt()
        );
    }

    private int totalActions(DemoSession session) {
        return session.getUsedActions() + session.getReservedActions();
    }

    private void validateCost(int cost) {
        if (cost < 1 || cost > ACTION_LIMIT) {
            throw new IllegalArgumentException("Demo action cost must be between 1 and " + ACTION_LIMIT);
        }
    }

    private DemoSessionRepository repository() {
        return sessionRepository.orElseThrow(() ->
            DemoSessionException.serviceUnavailable(new IllegalStateException("Demo repository is unavailable")));
    }

    public record QuotaSnapshot(int limit, int remaining, OffsetDateTime expiresAt) {}
}

package com.example.expensetracker.demo.quota;

import com.example.expensetracker.demo.security.DemoPrincipal;
import com.example.expensetracker.demo.session.DemoSession;
import com.example.expensetracker.demo.session.DemoSessionException;
import com.example.expensetracker.demo.session.DemoSessionRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoQuotaReservationService {

    private static final int RESERVATION_MINUTES = 2;
    private static final UUID PERSONAL_RESERVATION = new UUID(0, 0);

    private final DemoQuotaService quotaService;
    private final Optional<DemoSessionRepository> sessionRepository;

    public DemoQuotaReservationService(
        DemoQuotaService quotaService,
        Optional<DemoSessionRepository> sessionRepository
    ) {
        this.quotaService = quotaService;
        this.sessionRepository = sessionRepository;
    }

    @Transactional
    public UUID reserve(Authentication authentication, int cost) {
        if (!(authentication.getPrincipal() instanceof DemoPrincipal principal)) {
            return PERSONAL_RESERVATION;
        }
        DemoSession session = quotaService.lockForMutation(principal.sessionId(), cost);
        OffsetDateTime now = repository().databaseNow();
        UUID reservationId = UUID.randomUUID();
        repository().saveReservation(DemoQuotaReservation.builder()
            .id(reservationId)
            .demoSessionId(session.getId())
            .cost(cost)
            .state("PENDING")
            .createdAt(now)
            .expiresAt(now.plusMinutes(RESERVATION_MINUTES))
            .build());
        session.setReservedActions(session.getReservedActions() + cost);
        return reservationId;
    }

    @Transactional
    public void finalize(UUID reservationId) {
        complete(reservationId, true);
    }

    @Transactional
    public void release(UUID reservationId) {
        complete(reservationId, false);
    }

    private void complete(UUID reservationId, boolean used) {
        if (reservationId == null
            || PERSONAL_RESERVATION.equals(reservationId)
            || sessionRepository.isEmpty()) {
            return;
        }
        Optional<DemoQuotaReservation> existing = repository().findReservation(reservationId);
        if (existing.isEmpty()) {
            return;
        }
        DemoSession session = repository().lockActiveSession(existing.get().getDemoSessionId())
            .orElseThrow(DemoSessionException::sessionExpired);
        DemoQuotaReservation reservation = repository().lockPendingReservation(reservationId)
            .orElse(null);
        if (reservation == null) {
            return;
        }
        session.setReservedActions(session.getReservedActions() - reservation.getCost());
        if (used) {
            session.setUsedActions(session.getUsedActions() + reservation.getCost());
            reservation.setState("FINALIZED");
        } else {
            reservation.setState("RELEASED");
        }
    }

    private DemoSessionRepository repository() {
        return sessionRepository.orElseThrow(() ->
            DemoSessionException.serviceUnavailable(
                new IllegalStateException("Demo repository is unavailable")));
    }
}

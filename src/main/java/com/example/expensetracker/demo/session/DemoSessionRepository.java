package com.example.expensetracker.demo.session;

import com.example.expensetracker.demo.quota.DemoQuotaReservation;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("prod")
public class DemoSessionRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public OffsetDateTime databaseNow() {
        return parseOffsetDateTime(entityManager.createNativeQuery("""
            SELECT CONVERT(VARCHAR(40), CAST(SYSDATETIMEOFFSET() AS DATETIMEOFFSET(6)), 127)
            """).getSingleResult());
    }

    public Optional<DemoSession> findByResumeDigest(String resumeDigest) {
        return singleSession("""
            SELECT * FROM demo_session WHERE resume_token_digest = :resumeDigest
            """, resumeDigest);
    }

    public Optional<DemoSession> findActiveByResumeDigest(String resumeDigest) {
        return singleSession("""
            SELECT * FROM demo_session
            WHERE resume_token_digest = :resumeDigest
              AND status = 'ACTIVE'
              AND expires_at > SYSDATETIMEOFFSET()
            """, resumeDigest);
    }

    public int deleteExpiredData() {
        execute("""
            DELETE FROM chat_identity_mapping
            WHERE demo_session_id IN (
                SELECT id FROM demo_session WHERE expires_at <= SYSDATETIMEOFFSET()
            )
            """);
        execute("""
            DELETE FROM demo_quota_reservation
            WHERE demo_session_id IN (
                SELECT id FROM demo_session WHERE expires_at <= SYSDATETIMEOFFSET()
            )
            """);
        execute("""
            DELETE occurrence
            FROM recurring_expense_occurrence occurrence
            JOIN recurring_expense recurring ON recurring.id = occurrence.recurring_expense_id
            JOIN demo_session session_row ON session_row.id = recurring.demo_session_id
            WHERE session_row.expires_at <= SYSDATETIMEOFFSET()
            """);
        executeOwnedDelete("recurring_expense", "expires_at <= SYSDATETIMEOFFSET()");
        executeOwnedDelete("expense", "expires_at <= SYSDATETIMEOFFSET()");
        executeOwnedDelete("budget", "expires_at <= SYSDATETIMEOFFSET()");
        executeOwnedDelete("expense_category", "expires_at <= SYSDATETIMEOFFSET()");
        execute("DELETE FROM demo_access_token WHERE expires_at <= SYSDATETIMEOFFSET()");
        return entityManager.createNativeQuery(
            "DELETE FROM demo_session WHERE expires_at <= SYSDATETIMEOFFSET()")
            .executeUpdate();
    }

    public void ensureAdmissionRow(LocalDate anchorMonth, OffsetDateTime now) {
        entityManager.createNativeQuery("""
            MERGE demo_seed_state WITH (HOLDLOCK) AS target
            USING (SELECT CAST(1 AS TINYINT) AS id) AS source
            ON target.id = source.id
            WHEN NOT MATCHED THEN
                INSERT (id, template_version, anchor_month, refreshed_at)
                VALUES (source.id, 0, :anchorMonth, :now);
            """)
            .setParameter("anchorMonth", anchorMonth)
            .setParameter("now", now)
            .executeUpdate();
    }

    public void lockAdmissionRow() {
        entityManager.createNativeQuery("""
            SELECT template_version
            FROM demo_seed_state WITH (UPDLOCK, HOLDLOCK)
            WHERE id = 1
            """).getSingleResult();
    }

    public int activeSessionCount() {
        return ((Number) entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM demo_session
            WHERE status = 'ACTIVE' AND expires_at > SYSDATETIMEOFFSET()
            """).getSingleResult()).intValue();
    }

    public Optional<OffsetDateTime> earliestActiveExpiry() {
        Object value = entityManager.createNativeQuery("""
            SELECT CONVERT(VARCHAR(40), MIN(expires_at), 127) FROM demo_session
            WHERE status = 'ACTIVE' AND expires_at > SYSDATETIMEOFFSET()
            """).getSingleResult();
        return optionalOffsetDateTime(value);
    }

    public void save(DemoSession session) {
        entityManager.persist(session);
        entityManager.flush();
    }

    public Optional<DemoSession> lockActiveSession(UUID sessionId) {
        List<DemoSession> sessions = entityManager.createNativeQuery("""
            SELECT * FROM demo_session WITH (UPDLOCK, HOLDLOCK)
            WHERE id = :sessionId
              AND status = 'ACTIVE'
              AND expires_at > SYSDATETIMEOFFSET()
            """, DemoSession.class)
            .setParameter("sessionId", sessionId)
            .getResultList();
        return sessions.stream().findFirst();
    }

    public Optional<DemoSession> findActiveSession(UUID sessionId) {
        List<DemoSession> sessions = entityManager.createNativeQuery("""
            SELECT * FROM demo_session
            WHERE id = :sessionId
              AND status = 'ACTIVE'
              AND expires_at > SYSDATETIMEOFFSET()
            """, DemoSession.class)
            .setParameter("sessionId", sessionId)
            .getResultList();
        return sessions.stream().findFirst();
    }

    public void saveReservation(DemoQuotaReservation reservation) {
        entityManager.persist(reservation);
        entityManager.flush();
    }

    public Optional<DemoQuotaReservation> findReservation(UUID reservationId) {
        return Optional.ofNullable(entityManager.find(DemoQuotaReservation.class, reservationId));
    }

    public Optional<DemoQuotaReservation> lockPendingReservation(UUID reservationId) {
        List<DemoQuotaReservation> reservations = entityManager.createNativeQuery("""
            SELECT * FROM demo_quota_reservation WITH (UPDLOCK, HOLDLOCK)
            WHERE id = :reservationId AND state = 'PENDING'
            """, DemoQuotaReservation.class)
            .setParameter("reservationId", reservationId)
            .getResultList();
        return reservations.stream().findFirst();
    }

    public void reclaimExpiredReservations(DemoSession session, OffsetDateTime now) {
        int expiredCost = ((Number) entityManager.createNativeQuery("""
            SELECT COALESCE(SUM(cost), 0)
            FROM demo_quota_reservation WITH (UPDLOCK, HOLDLOCK)
            WHERE demo_session_id = :sessionId
              AND state = 'PENDING'
              AND expires_at <= :now
            """)
            .setParameter("sessionId", session.getId())
            .setParameter("now", now)
            .getSingleResult()).intValue();
        if (expiredCost == 0) {
            return;
        }
        entityManager.createNativeQuery("""
            UPDATE demo_quota_reservation
            SET state = 'EXPIRED'
            WHERE demo_session_id = :sessionId
              AND state = 'PENDING'
              AND expires_at <= :now
            """)
            .setParameter("sessionId", session.getId())
            .setParameter("now", now)
            .executeUpdate();
        session.setReservedActions(Math.max(0, session.getReservedActions() - expiredCost));
    }

    public void deleteOwnedData(UUID sessionId) {
        executeForSession("DELETE FROM chat_identity_mapping WHERE demo_session_id = :sessionId", sessionId);
        executeForSession("DELETE FROM demo_quota_reservation WHERE demo_session_id = :sessionId", sessionId);
        executeForSession("""
            DELETE occurrence
            FROM recurring_expense_occurrence occurrence
            JOIN recurring_expense recurring ON recurring.id = occurrence.recurring_expense_id
            WHERE recurring.demo_session_id = :sessionId
            """, sessionId);
        executeForSession("DELETE FROM recurring_expense WHERE demo_session_id = :sessionId", sessionId);
        executeForSession("DELETE FROM expense WHERE demo_session_id = :sessionId", sessionId);
        executeForSession("DELETE FROM budget WHERE demo_session_id = :sessionId", sessionId);
        executeForSession("DELETE FROM expense_category WHERE demo_session_id = :sessionId", sessionId);
        executeForSession("DELETE FROM demo_access_token WHERE demo_session_id = :sessionId", sessionId);
    }

    public void markLoggedOut(UUID sessionId, String invalidResumeDigest) {
        entityManager.createNativeQuery("""
            UPDATE demo_session
            SET status = 'LOGGED_OUT',
                expires_at = SYSDATETIMEOFFSET(),
                resume_token_digest = :resumeDigest
            WHERE id = :sessionId
            """)
            .setParameter("resumeDigest", invalidResumeDigest)
            .setParameter("sessionId", sessionId)
            .executeUpdate();
        entityManager.flush();
    }

    public void deleteOldAttempts() {
        execute("""
            DELETE FROM demo_session_attempt
            WHERE attempted_at <= DATEADD(HOUR, -1, SYSDATETIMEOFFSET())
            """);
    }

    public int attemptCountForIp(String ipDigest) {
        return ((Number) entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM demo_session_attempt
            WHERE ip_digest = :ipDigest
              AND attempted_at > DATEADD(HOUR, -1, SYSDATETIMEOFFSET())
            """)
            .setParameter("ipDigest", ipDigest)
            .getSingleResult()).intValue();
    }

    public int globalAttemptCount() {
        return ((Number) entityManager.createNativeQuery("""
            SELECT COUNT(*) FROM demo_session_attempt
            WHERE attempted_at > DATEADD(HOUR, -1, SYSDATETIMEOFFSET())
            """).getSingleResult()).intValue();
    }

    public Optional<OffsetDateTime> oldestAttempt(String ipDigest, boolean global) {
        String predicate = global ? "" : "AND ip_digest = :ipDigest";
        var query = entityManager.createNativeQuery("""
            SELECT CONVERT(VARCHAR(40), MIN(attempted_at), 127) FROM demo_session_attempt
            WHERE attempted_at > DATEADD(HOUR, -1, SYSDATETIMEOFFSET())
            """ + predicate);
        if (!global) {
            query.setParameter("ipDigest", ipDigest);
        }
        return optionalOffsetDateTime(query.getSingleResult());
    }

    public void recordAttempt(String ipDigest) {
        entityManager.createNativeQuery("""
            INSERT INTO demo_session_attempt (ip_digest, attempted_at)
            VALUES (:ipDigest, SYSDATETIMEOFFSET())
            """)
            .setParameter("ipDigest", ipDigest)
            .executeUpdate();
    }

    private Optional<DemoSession> singleSession(String sql, String resumeDigest) {
        List<DemoSession> sessions = entityManager.createNativeQuery(sql, DemoSession.class)
            .setParameter("resumeDigest", resumeDigest)
            .getResultList();
        return sessions.stream().findFirst();
    }

    private void executeOwnedDelete(String table, String sessionPredicate) {
        execute("DELETE FROM " + table + " WHERE demo_session_id IN "
            + "(SELECT id FROM demo_session WHERE " + sessionPredicate + ")");
    }

    private void execute(String sql) {
        entityManager.createNativeQuery(sql).executeUpdate();
    }

    private void executeForSession(String sql, UUID sessionId) {
        entityManager.createNativeQuery(sql)
            .setParameter("sessionId", sessionId)
            .executeUpdate();
    }

    private static Optional<OffsetDateTime> optionalOffsetDateTime(Object value) {
        return value == null ? Optional.empty() : Optional.of(parseOffsetDateTime(value));
    }

    private static OffsetDateTime parseOffsetDateTime(Object value) {
        return OffsetDateTime.parse(value.toString());
    }
}

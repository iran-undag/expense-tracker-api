package com.example.expensetracker.demo.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.example.expensetracker.demo.security.DemoTokenDigester;
import com.example.expensetracker.demo.seed.DemoDatabaseInitializer;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;

@SpringBootTest
@ActiveProfiles("prod")
class DemoSessionConcurrencyTest {

    private static final String EXTERNAL_JDBC_URL = System.getProperty("demo.test.jdbc-url");
    private static final MSSQLServerContainer<?> SQL_SERVER = startSqlServerWhenNeeded();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DemoSessionConcurrencyTest::jdbcUrl);
        registry.add("spring.datasource.username", DemoSessionConcurrencyTest::username);
        registry.add("spring.datasource.password", DemoSessionConcurrencyTest::password);
        registry.add("demo.datasource.url", DemoSessionConcurrencyTest::jdbcUrl);
        registry.add("demo.datasource.username", DemoSessionConcurrencyTest::username);
        registry.add("demo.datasource.password", DemoSessionConcurrencyTest::password);
        registry.add("demo.token-hmac-key", () -> "0123456789abcdef0123456789abcdef");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://localhost/unused-jwks");
    }

    @AfterAll
    static void stopManagedSqlServer() {
        if (SQL_SERVER != null) {
            SQL_SERVER.stop();
        }
    }

    @Autowired
    private DemoSessionFacade facade;

    @Autowired
    private DemoDatabaseInitializer databaseInitializer;

    @Autowired
    private DemoTokenDigester digester;

    @Autowired
    @Qualifier("demoJdbcTemplate")
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetDemoData() {
        databaseInitializer.ensureMigrated();
        deleteAllDemoData();
    }

    @AfterEach
    void removeRollbackTrigger() {
        jdbc.execute("DROP TRIGGER IF EXISTS reject_demo_logout");
    }

    @Test
    void createsOneHourSessionWithTenActions() {
        DemoSessionService.SessionGrant grant = facade.createOrResume(null);
        UUID sessionId = sessionIdForAccessToken(grant.response().accessToken());

        assertThat(grant.response().actionLimit()).isEqualTo(10);
        assertThat(jdbc.queryForObject("""
            SELECT DATEDIFF(SECOND, created_at, expires_at)
            FROM demo_session WHERE id = ?
            """, Integer.class, sessionId)).isEqualTo(3_600);
    }

    @Test
    void serializableAdmissionAllowsExactlyTwoOfThreeConcurrentSessions() throws Exception {
        List<DemoSessionResult> results = runConcurrentCreations(3);

        assertThat(results).filteredOn(DemoSessionResult::created).hasSize(2);
        assertThat(results).filteredOn(DemoSessionResult::capacityRejected).hasSize(1);
        assertThat(activeSessionCount()).isEqualTo(2);
        assertThat(admissionCount()).isEqualTo(2);
    }

    @Test
    void allowsFourSuccessfulCreationsPerHourAndDoesNotCountRejection() {
        for (int index = 0; index < 4; index++) {
            DemoSessionService.SessionGrant grant = facade.createOrResume(null);
            facade.logout(sessionIdForAccessToken(grant.response().accessToken()));
        }

        assertThat(admissionCount()).isEqualTo(4);
        assertThatThrownBy(() -> facade.createOrResume(null))
            .isInstanceOfSatisfying(DemoSessionException.class, exception -> {
                assertThat(exception.code()).isEqualTo("DEMO_CAPACITY_REACHED");
                assertThat(exception.retryAfterSeconds()).isBetween(1L, 3_600L);
            });
        assertThat(admissionCount()).isEqualTo(4);
    }

    @Test
    void admitsNextSessionWhenOldestHourlyAdmissionAgesOut() {
        for (int index = 0; index < 4; index++) {
            DemoSessionService.SessionGrant grant = facade.createOrResume(null);
            facade.logout(sessionIdForAccessToken(grant.response().accessToken()));
        }
        jdbc.update("""
            UPDATE demo_session_admission
            SET admitted_at = DATEADD(SECOND, -3601, SYSDATETIMEOFFSET())
            WHERE id = (SELECT MIN(id) FROM demo_session_admission)
            """);

        facade.createOrResume(null);

        assertThat(admissionCount()).isEqualTo(5);
    }

    @Test
    void allowsTwelfthDailyAdmissionAndRejectsThirteenth() {
        for (int hoursAgo = 2; hoursAgo <= 12; hoursAgo++) {
            insertAdmissionHoursAgo(hoursAgo);
        }

        DemoSessionService.SessionGrant twelfth = facade.createOrResume(null);
        facade.logout(sessionIdForAccessToken(twelfth.response().accessToken()));

        assertThat(admissionCount()).isEqualTo(12);
        assertThatThrownBy(() -> facade.createOrResume(null))
            .isInstanceOfSatisfying(DemoSessionException.class,
                exception -> assertThat(exception.code()).isEqualTo("DEMO_CAPACITY_REACHED"));
        assertThat(admissionCount()).isEqualTo(12);
    }

    @Test
    void returnsLongerDailyRetryWhenBothWindowsAreFull() {
        for (int index = 0; index < 4; index++) {
            jdbc.update("""
                INSERT INTO demo_session_admission (admitted_at)
                VALUES (DATEADD(MINUTE, -10, SYSDATETIMEOFFSET()))
                """);
        }
        for (int hoursAgo = 2; hoursAgo <= 9; hoursAgo++) {
            insertAdmissionHoursAgo(hoursAgo);
        }

        assertThatThrownBy(() -> facade.createOrResume(null))
            .isInstanceOfSatisfying(DemoSessionException.class,
                exception -> assertThat(exception.retryAfterSeconds()).isBetween(53_990L, 54_000L));
    }

    @Test
    void concurrentHourlyBoundaryAllowsOnlyFourthAdmission() throws Exception {
        for (int index = 0; index < 3; index++) {
            jdbc.update("""
                INSERT INTO demo_session_admission (admitted_at)
                VALUES (DATEADD(MINUTE, -10, SYSDATETIMEOFFSET()))
                """);
        }

        List<DemoSessionResult> results = runConcurrentCreations(2);

        assertThat(results).filteredOn(DemoSessionResult::created).hasSize(1);
        assertThat(results).filteredOn(DemoSessionResult::capacityRejected).hasSize(1);
        assertThat(admissionCount()).isEqualTo(4);
    }

    @Test
    void concurrentDailyBoundaryAllowsOnlyTwelfthAdmission() throws Exception {
        for (int hoursAgo = 2; hoursAgo <= 12; hoursAgo++) {
            insertAdmissionHoursAgo(hoursAgo);
        }

        List<DemoSessionResult> results = runConcurrentCreations(2);

        assertThat(results).filteredOn(DemoSessionResult::created).hasSize(1);
        assertThat(results).filteredOn(DemoSessionResult::capacityRejected).hasSize(1);
        assertThat(admissionCount()).isEqualTo(12);
    }

    @Test
    void cleansExpiredSessionsBeforeAdmissionAndRejectsTheirResumeCookies() {
        DemoSessionService.SessionGrant expired = facade.createOrResume(null);
        UUID expiredSessionId = sessionIdForAccessToken(expired.response().accessToken());
        jdbc.update("UPDATE demo_session SET expires_at = DATEADD(SECOND, -1, SYSDATETIMEOFFSET()) WHERE id = ?",
            expiredSessionId);

        assertThatThrownBy(() -> facade.createOrResume(expired.resumeToken()))
            .isInstanceOfSatisfying(DemoSessionException.class,
                exception -> assertThat(exception.code()).isEqualTo("DEMO_SESSION_EXPIRED"));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(
            () -> assertThat(sessionCount(expiredSessionId)).isZero());

        facade.createOrResume(null);
        facade.createOrResume(null);
        assertThat(activeSessionCount()).isEqualTo(2);
    }

    @Test
    void resumesValidCookieWithoutSlidingExpiryOrConsumingCreationThrottle() {
        DemoSessionService.SessionGrant created = facade.createOrResume(null);
        UUID sessionId = sessionIdForAccessToken(created.response().accessToken());

        DemoSessionService.SessionGrant resumed = created;
        for (int index = 0; index < 7; index++) {
            resumed = index % 2 == 0
                ? facade.createOrResume(created.resumeToken())
                : facade.renew(created.resumeToken());
        }

        assertThat(sessionIdForAccessToken(resumed.response().accessToken())).isEqualTo(sessionId);
        assertThat(resumed.response().sessionExpiresAt()).isEqualTo(created.response().sessionExpiresAt());
        assertThat(admissionCount()).isEqualTo(1);
    }

    @Test
    void terminalRenewalsNeverCreateSessionOrAdmission() {
        assertThatThrownBy(() -> facade.renew(null))
            .isInstanceOfSatisfying(DemoSessionException.class,
                exception -> assertThat(exception.code()).isEqualTo("DEMO_SESSION_EXPIRED"));
        assertThatThrownBy(() -> facade.renew("invalid-cookie"))
            .isInstanceOfSatisfying(DemoSessionException.class,
                exception -> assertThat(exception.code()).isEqualTo("DEMO_SESSION_EXPIRED"));
        assertThat(admissionCount()).isZero();
        assertThat(activeSessionCount()).isZero();

        DemoSessionService.SessionGrant loggedOut = facade.createOrResume(null);
        UUID loggedOutId = sessionIdForAccessToken(loggedOut.response().accessToken());
        facade.logout(loggedOutId);
        assertThatThrownBy(() -> facade.renew(loggedOut.resumeToken()))
            .isInstanceOfSatisfying(DemoSessionException.class,
                exception -> assertThat(exception.code()).isEqualTo("DEMO_SESSION_EXPIRED"));
        assertThat(admissionCount()).isEqualTo(1);
    }

    @Test
    void resumePrunesExpiredAccessTokensButRetainsUnexpiredTokensForActiveSession() {
        DemoSessionService.SessionGrant created = facade.createOrResume(null);
        UUID sessionId = sessionIdForAccessToken(created.response().accessToken());
        String expiredDigest = digester.digest("dmo_expired-token");
        jdbc.update("""
            INSERT INTO demo_access_token
                (demo_session_id, token_digest, created_at, expires_at)
            VALUES (?, ?, DATEADD(MINUTE, -16, SYSDATETIMEOFFSET()),
                DATEADD(MINUTE, -1, SYSDATETIMEOFFSET()))
            """, sessionId, expiredDigest);

        facade.createOrResume(created.resumeToken());

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(jdbc.queryForObject(
            "SELECT COUNT(*) FROM demo_access_token WHERE token_digest = ?",
            Integer.class,
            expiredDigest
        )).isZero());
        assertThat(ownedRowCount("demo_access_token", sessionId)).isEqualTo(2);
    }

    @Test
    void invalidResumeCookieCreatesAReplacementSession() {
        DemoSessionService.SessionGrant created = facade.createOrResume("invalid-cookie");

        assertThat(created.resumeToken()).isNotEqualTo("invalid-cookie");
        assertThat(activeSessionCount()).isEqualTo(1);
        assertThat(admissionCount()).isEqualTo(1);
    }

    @Test
    void logoutInvalidatesSessionAndDefersOwnedDataDeletion() {
        DemoSessionService.SessionGrant grant = facade.createOrResume(null);
        UUID sessionId = sessionIdForAccessToken(grant.response().accessToken());
        insertOwnedRows(sessionId);

        facade.logout(sessionId);

        assertThat(ownedRowCount("chat_identity_mapping", sessionId)).isEqualTo(1);
        assertThat(ownedRowCount("demo_quota_reservation", sessionId)).isEqualTo(1);
        assertThat(ownedRowCount("recurring_expense", sessionId)).isEqualTo(1);
        assertThat(ownedRowCount("expense", sessionId)).isEqualTo(1);
        assertThat(ownedRowCount("budget", sessionId)).isEqualTo(1);
        assertThat(ownedRowCount("expense_category", sessionId)).isEqualTo(1);
        assertThat(ownedRowCount("demo_access_token", sessionId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM demo_session WHERE id = ?", String.class, sessionId))
            .isEqualTo("LOGGED_OUT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_session WHERE id = ? AND expires_at <= SYSDATETIMEOFFSET()",
            Integer.class, sessionId)).isEqualTo(1);
    }

    @Test
    void logoutRollsBackCleanupWhenFinalSessionUpdateFails() {
        DemoSessionService.SessionGrant grant = facade.createOrResume(null);
        UUID sessionId = sessionIdForAccessToken(grant.response().accessToken());
        insertOwnedRows(sessionId);
        int accessTokensBefore = ownedRowCount("demo_access_token", sessionId);
        jdbc.execute("""
            CREATE TRIGGER reject_demo_logout ON demo_session
            AFTER UPDATE AS
            IF EXISTS (SELECT 1 FROM inserted WHERE status = 'LOGGED_OUT')
                THROW 51000, 'reject logout for rollback test', 1
            """);

        assertThatThrownBy(() -> facade.logout(sessionId)).isInstanceOf(RuntimeException.class);

        assertThat(ownedRowCount("expense_category", sessionId)).isEqualTo(1);
        assertThat(ownedRowCount("demo_access_token", sessionId)).isEqualTo(accessTokensBefore);
        assertThat(jdbc.queryForObject("SELECT status FROM demo_session WHERE id = ?", String.class, sessionId))
            .isEqualTo("ACTIVE");
    }

    private UUID sessionIdForAccessToken(String rawAccessToken) {
        return jdbc.queryForObject("SELECT demo_session_id FROM demo_access_token WHERE token_digest = ?",
            UUID.class, digester.digest(rawAccessToken));
    }

    private int activeSessionCount() {
        return jdbc.queryForObject("""
            SELECT COUNT(*) FROM demo_session
            WHERE status = 'ACTIVE' AND expires_at > SYSDATETIMEOFFSET()
            """, Integer.class);
    }

    private int sessionCount(UUID sessionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM demo_session WHERE id = ?", Integer.class, sessionId);
    }

    private int admissionCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM demo_session_admission", Integer.class);
    }

    private void insertAdmissionHoursAgo(int hoursAgo) {
        jdbc.update("""
            INSERT INTO demo_session_admission (admitted_at)
            VALUES (DATEADD(HOUR, ?, SYSDATETIMEOFFSET()))
            """, -hoursAgo);
    }

    private List<DemoSessionResult> runConcurrentCreations(int requestCount) throws Exception {
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        try {
            List<Callable<DemoSessionResult>> tasks = new ArrayList<>();
            for (int index = 0; index < requestCount; index++) {
                tasks.add(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        facade.createOrResume(null);
                        return new DemoSessionResult(true, false);
                    } catch (DemoSessionException exception) {
                        return new DemoSessionResult(false,
                            exception.code().equals("DEMO_CAPACITY_REACHED"));
                    }
                });
            }

            List<Future<DemoSessionResult>> futures = tasks.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();
            List<DemoSessionResult> results = new ArrayList<>();
            for (Future<DemoSessionResult> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    private int ownedRowCount(String table, UUID sessionId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE demo_session_id = ?",
            Integer.class, sessionId);
    }

    private void insertOwnedRows(UUID sessionId) {
        String owner = "demo:" + sessionId;
        jdbc.update("""
            INSERT INTO expense_category
                (userid, name, color, icon, system_default, active, demo_session_id, is_demo_seed)
            VALUES (?, 'Owned', '#000000', 'wallet', 0, 1, ?, 0)
            """, owner, sessionId);
        jdbc.update("""
            INSERT INTO budget
                (userid, category, budget_year, budget_month, amount, demo_session_id, is_demo_seed)
            VALUES (?, 'Owned', 2026, 8, ?, ?, 0)
            """, owner, new BigDecimal("100.00"), sessionId);
        jdbc.update("""
            INSERT INTO expense
                (description, amount, date, category, userid, demo_session_id, is_demo_seed)
            VALUES ('Owned', ?, '2026-08-12', 'Owned', ?, ?, 0)
            """, new BigDecimal("10.00"), owner, sessionId);
        Long expenseId = jdbc.queryForObject("SELECT MAX(id) FROM expense WHERE demo_session_id = ?",
            Long.class, sessionId);
        jdbc.update("""
            INSERT INTO recurring_expense
                (userid, description, amount, category, frequency, start_date, next_run_date, active,
                 demo_session_id, is_demo_seed)
            VALUES (?, 'Owned', ?, 'Owned', 'MONTHLY', '2026-08-01', '2026-09-01', 1, ?, 0)
            """, owner, new BigDecimal("10.00"), sessionId);
        Long recurringId = jdbc.queryForObject("SELECT MAX(id) FROM recurring_expense WHERE demo_session_id = ?",
            Long.class, sessionId);
        jdbc.update("""
            INSERT INTO recurring_expense_occurrence
                (recurring_expense_id, userid, occurrence_date, expense_id)
            VALUES (?, ?, '2026-08-12', ?)
            """, recurringId, owner, expenseId);
        jdbc.update("""
            INSERT INTO demo_quota_reservation
                (id, demo_session_id, cost, state, created_at, expires_at)
            VALUES (?, ?, 1, 'PENDING', SYSDATETIMEOFFSET(), DATEADD(MINUTE, 5, SYSDATETIMEOFFSET()))
            """, UUID.randomUUID(), sessionId);
        jdbc.update("""
            INSERT INTO chat_identity_mapping
                (direct_line_user_id, conversation_id, userid, expires_at, created_at, demo_session_id)
            VALUES (?, ?, ?, DATEADD(MINUTE, 5, SYSUTCDATETIME()), SYSUTCDATETIME(), ?)
            """, "user-" + sessionId, "conversation-" + sessionId, owner, sessionId);
    }

    private void deleteAllDemoData() {
        jdbc.update("DELETE FROM chat_identity_mapping WHERE demo_session_id IS NOT NULL");
        jdbc.update("DELETE FROM demo_quota_reservation");
        jdbc.update("DELETE FROM recurring_expense_occurrence WHERE userid LIKE 'demo:%'");
        jdbc.update("DELETE FROM recurring_expense WHERE demo_session_id IS NOT NULL");
        jdbc.update("DELETE FROM expense WHERE demo_session_id IS NOT NULL");
        jdbc.update("DELETE FROM budget WHERE demo_session_id IS NOT NULL");
        jdbc.update("DELETE FROM expense_category WHERE demo_session_id IS NOT NULL");
        jdbc.update("DELETE FROM demo_access_token");
        jdbc.update("DELETE FROM demo_session");
        jdbc.update("DELETE FROM demo_session_admission");
        jdbc.update("DELETE FROM demo_seed_state");
    }

    private record DemoSessionResult(boolean created, boolean capacityRejected) {
    }

    private static MSSQLServerContainer<?> startSqlServerWhenNeeded() {
        if (EXTERNAL_JDBC_URL != null) {
            return null;
        }
        MSSQLServerContainer<?> sqlServer =
            new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest").acceptLicense();
        sqlServer.start();
        return sqlServer;
    }

    private static String jdbcUrl() {
        return SQL_SERVER == null ? EXTERNAL_JDBC_URL : SQL_SERVER.getJdbcUrl();
    }

    private static String username() {
        return SQL_SERVER == null ? System.getProperty("demo.test.username", "sa") : SQL_SERVER.getUsername();
    }

    private static String password() {
        return SQL_SERVER == null
            ? System.getProperty("demo.test.password", "Task4-SqlServer-Password1!")
            : SQL_SERVER.getPassword();
    }
}

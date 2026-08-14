package com.example.expensetracker.demo.quota;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expensetracker.demo.security.DemoPrincipal;
import com.example.expensetracker.demo.security.DemoTokenDigester;
import com.example.expensetracker.demo.seed.DemoDatabaseInitializer;
import com.example.expensetracker.demo.session.DemoSessionFacade;
import com.example.expensetracker.persistence.DataRealm;
import com.example.expensetracker.persistence.DataRealmExecutor;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MSSQLServerContainer;

@SpringBootTest
@ActiveProfiles("prod")
class DemoExternalOperationQuotaTest {

    private static final String EXTERNAL_JDBC_URL = System.getProperty("demo.test.jdbc-url");
    private static final MSSQLServerContainer<?> SQL_SERVER = startSqlServerWhenNeeded();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DemoExternalOperationQuotaTest::jdbcUrl);
        registry.add("spring.datasource.username", DemoExternalOperationQuotaTest::username);
        registry.add("spring.datasource.password", DemoExternalOperationQuotaTest::password);
        registry.add("demo.datasource.url", DemoExternalOperationQuotaTest::jdbcUrl);
        registry.add("demo.datasource.username", DemoExternalOperationQuotaTest::username);
        registry.add("demo.datasource.password", DemoExternalOperationQuotaTest::password);
        registry.add("demo.token-hmac-key", () -> "0123456789abcdef0123456789abcdef");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://localhost/unused-jwks");
    }

    @Autowired private DemoSessionFacade sessionFacade;
    @Autowired private DemoQuotaReservationService reservationService;
    @Autowired private DemoMutationExecutor mutationExecutor;
    @Autowired private DemoDatabaseInitializer databaseInitializer;
    @Autowired private DemoTokenDigester digester;
    @Autowired private DataRealmExecutor realmExecutor;
    @Autowired @Qualifier("demoJdbcTemplate") private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        databaseInitializer.ensureMigrated();
        deleteAllDemoData();
    }

    @AfterAll
    static void stopManagedSqlServer() {
        if (SQL_SERVER != null) {
            SQL_SERVER.stop();
        }
    }

    @Test
    void finalizeMovesReservedCostToUsedActions() {
        TestSession session = createSession("198.51.100.81");

        UUID reservationId = inDemoRealm(() -> reservationService.reserve(session.authentication(), 1));
        assertThat(actions(session.sessionId())).containsExactly(0, 1);

        inDemoRealm(() -> reservationService.finalize(reservationId));

        assertThat(actions(session.sessionId())).containsExactly(1, 0);
        assertThat(reservationState(reservationId)).isEqualTo("FINALIZED");
    }

    @Test
    void releaseReturnsReservedCostWithoutUsingAnAction() {
        TestSession session = createSession("198.51.100.82");
        UUID reservationId = inDemoRealm(() -> reservationService.reserve(session.authentication(), 1));

        inDemoRealm(() -> reservationService.release(reservationId));

        assertThat(actions(session.sessionId())).containsExactly(0, 0);
        assertThat(reservationState(reservationId)).isEqualTo("RELEASED");
    }

    @Test
    void laterMutationReclaimsExpiredPendingReservation() {
        TestSession session = createSession("198.51.100.83");
        UUID reservationId = inDemoRealm(() -> reservationService.reserve(session.authentication(), 1));
        jdbc.update("UPDATE demo_quota_reservation SET expires_at = DATEADD(MINUTE, -1, SYSDATETIMEOFFSET()) WHERE id = ?",
            reservationId);

        String result = inDemoRealm(() -> mutationExecutor.execute(
            session.authentication(), 1, () -> "mutated"));

        assertThat(result).isEqualTo("mutated");
        assertThat(actions(session.sessionId())).containsExactly(1, 0);
        assertThat(reservationState(reservationId)).isEqualTo("EXPIRED");
    }

    private TestSession createSession(String address) {
        var grant = sessionFacade.createOrResume(null, address);
        UUID sessionId = jdbc.queryForObject(
            "SELECT demo_session_id FROM demo_access_token WHERE token_digest = ?",
            UUID.class, digester.digest(grant.response().accessToken()));
        DemoPrincipal principal = jdbc.queryForObject("""
            SELECT shared_account_id, persistence_owner_id, expires_at
            FROM demo_session WHERE id = ?
            """, (resultSet, rowNum) -> new DemoPrincipal(
                sessionId,
                resultSet.getString("shared_account_id"),
                resultSet.getString("persistence_owner_id"),
                resultSet.getObject("expires_at", OffsetDateTime.class)
            ), sessionId);
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
            principal, null, List.of());
        return new TestSession(sessionId, authentication);
    }

    private List<Integer> actions(UUID sessionId) {
        return jdbc.queryForObject(
            "SELECT used_actions, reserved_actions FROM demo_session WHERE id = ?",
            (resultSet, rowNum) -> List.of(
                resultSet.getInt("used_actions"), resultSet.getInt("reserved_actions")),
            sessionId);
    }

    private String reservationState(UUID reservationId) {
        return jdbc.queryForObject(
            "SELECT state FROM demo_quota_reservation WHERE id = ?", String.class, reservationId);
    }

    private <T> T inDemoRealm(java.util.function.Supplier<T> work) {
        return realmExecutor.inRealm(DataRealm.DEMO, work);
    }

    private void inDemoRealm(Runnable work) {
        realmExecutor.inRealm(DataRealm.DEMO, work);
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
        jdbc.update("DELETE FROM demo_session_attempt");
        jdbc.update("DELETE FROM demo_seed_state");
    }

    private record TestSession(UUID sessionId, Authentication authentication) {}

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

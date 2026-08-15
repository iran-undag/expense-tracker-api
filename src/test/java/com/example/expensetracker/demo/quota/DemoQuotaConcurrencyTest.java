package com.example.expensetracker.demo.quota;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.expensetracker.demo.security.DemoPrincipal;
import com.example.expensetracker.demo.security.DemoTokenDigester;
import com.example.expensetracker.demo.seed.DemoDatabaseInitializer;
import com.example.expensetracker.demo.session.DemoSessionException;
import com.example.expensetracker.demo.session.DemoSessionFacade;
import com.example.expensetracker.model.Expense;
import com.example.expensetracker.model.RecurringExpense;
import com.example.expensetracker.model.RecurringFrequency;
import com.example.expensetracker.persistence.DataRealm;
import com.example.expensetracker.persistence.DataRealmExecutor;
import com.example.expensetracker.security.UserDataScope;
import com.example.expensetracker.service.ExpenseService;
import com.example.expensetracker.service.RecurringExpenseService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class DemoQuotaConcurrencyTest {

    private static final String EXTERNAL_JDBC_URL = System.getProperty("demo.test.jdbc-url");
    private static final MSSQLServerContainer<?> SQL_SERVER = startSqlServerWhenNeeded();

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DemoQuotaConcurrencyTest::jdbcUrl);
        registry.add("spring.datasource.username", DemoQuotaConcurrencyTest::username);
        registry.add("spring.datasource.password", DemoQuotaConcurrencyTest::password);
        registry.add("demo.datasource.url", DemoQuotaConcurrencyTest::jdbcUrl);
        registry.add("demo.datasource.username", DemoQuotaConcurrencyTest::username);
        registry.add("demo.datasource.password", DemoQuotaConcurrencyTest::password);
        registry.add("demo.token-hmac-key", () -> "0123456789abcdef0123456789abcdef");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
            () -> "http://localhost/unused-jwks");
    }

    @Autowired private DemoSessionFacade sessionFacade;
    @Autowired private DemoMutationExecutor mutationExecutor;
    @Autowired private DemoDatabaseInitializer databaseInitializer;
    @Autowired private DataRealmExecutor realmExecutor;
    @Autowired private DemoTokenDigester digester;
    @Autowired private ExpenseService expenseService;
    @Autowired private RecurringExpenseService recurringExpenseService;
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
    void failedBusinessMutationRollsBackDataAndQuota() {
        TestSession session = createSession("198.51.100.71");

        assertThatThrownBy(() -> inDemoRealm(() -> mutationExecutor.execute(
            session.authentication(),
            1,
            () -> {
                expenseService.saveExpense(session.scope(), expense("Will roll back"));
                throw new IllegalStateException("business failure");
            }
        ))).isInstanceOf(IllegalStateException.class);

        assertThat(usedActions(session.principal().sessionId())).isZero();
        assertThat(ownedExpenseCount(session.principal().sessionId())).isZero();
    }

    @Test
    void parallelMutationsConsumeExactlyTheLimitAndRejectTheNextAction() throws Exception {
        TestSession session = createSession("198.51.100.72");
        CountDownLatch ready = new CountDownLatch(15);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(15);
        try {
            List<Callable<Integer>> tasks = new ArrayList<>();
            for (int index = 0; index < 15; index++) {
                int expenseIndex = index;
                tasks.add(() -> {
                    ready.countDown();
                    start.await();
                    inDemoRealm(() -> mutationExecutor.execute(
                        session.authentication(),
                        1,
                        () -> expenseService.saveExpense(session.scope(), expense("Expense " + expenseIndex))
                    ));
                    return totalActions(session.principal().sessionId());
                });
            }

            List<Future<Integer>> futures = tasks.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();
            for (Future<Integer> future : futures) {
                assertThat(future.get()).isBetween(1, 15);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(usedActions(session.principal().sessionId())).isEqualTo(15);
        assertThat(totalActions(session.principal().sessionId())).isEqualTo(15);
        assertThat(ownedExpenseCount(session.principal().sessionId())).isEqualTo(15);
        assertThatThrownBy(() -> inDemoRealm(() -> mutationExecutor.execute(
            session.authentication(),
            1,
            () -> expenseService.saveExpense(session.scope(), expense("Over limit"))
        ))).isInstanceOfSatisfying(DemoSessionException.class,
            exception -> assertThat(exception.code()).isEqualTo("DEMO_QUOTA_EXHAUSTED"));
        assertThat(totalActions(session.principal().sessionId())).isEqualTo(15);
        assertThat(ownedExpenseCount(session.principal().sessionId())).isEqualTo(15);
    }

    @Test
    void recurringGenerationStopsAtTheQuotaBoundaryAndLeavesTheNextOccurrenceDue() {
        TestSession session = createSession("198.51.100.73");
        LocalDate today = LocalDate.of(2026, 8, 14);
        LocalDate firstOccurrence = today.minusDays(24);
        RecurringExpense rule = inDemoRealm(() -> recurringExpenseService.saveRecurringExpense(
            session.scope(),
            RecurringExpense.builder()
                .description("Daily demo rule")
                .amount(BigDecimal.ONE)
                .category("Other")
                .frequency(RecurringFrequency.DAILY)
                .startDate(firstOccurrence)
                .active(true)
                .build()
        ));

        int generated = inDemoRealm(() -> recurringExpenseService.generateDueExpenses(session.scope(), today));

        assertThat(generated).isEqualTo(15);
        assertThat(totalActions(session.principal().sessionId())).isEqualTo(15);
        assertThat(ownedExpenseCount(session.principal().sessionId())).isEqualTo(15);
        assertThat(jdbc.queryForObject(
            "SELECT next_run_date FROM recurring_expense WHERE id = ?",
            LocalDate.class,
            rule.getId()
        )).isEqualTo(firstOccurrence.plusDays(15));
        assertThat(inDemoRealm(() -> recurringExpenseService.generateDueExpenses(session.scope(), today))).isZero();
        assertThat(totalActions(session.principal().sessionId())).isEqualTo(15);
    }

    private TestSession createSession(String address) {
        var grant = sessionFacade.createOrResume(null, address);
        UUID sessionId = jdbc.queryForObject("""
            SELECT demo_session_id FROM demo_access_token WHERE token_digest = ?
            """, UUID.class, digester.digest(grant.response().accessToken()));
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
        UserDataScope scope = new UserDataScope(
            principal.persistenceOwnerId(),
            List.of("demo:seed", principal.persistenceOwnerId()),
            principal.sessionId(),
            true
        );
        return new TestSession(principal, authentication, scope);
    }

    private Expense expense(String description) {
        return Expense.builder()
            .description(description)
            .amount(BigDecimal.ONE)
            .date(LocalDate.of(2026, 8, 14))
            .category("Other")
            .build();
    }

    private <T> T inDemoRealm(java.util.function.Supplier<T> work) {
        return realmExecutor.inRealm(DataRealm.DEMO, work);
    }

    private int usedActions(UUID sessionId) {
        return jdbc.queryForObject("SELECT used_actions FROM demo_session WHERE id = ?", Integer.class, sessionId);
    }

    private int totalActions(UUID sessionId) {
        return jdbc.queryForObject(
            "SELECT used_actions + reserved_actions FROM demo_session WHERE id = ?",
            Integer.class,
            sessionId
        );
    }

    private int ownedExpenseCount(UUID sessionId) {
        return jdbc.queryForObject(
            "SELECT COUNT(*) FROM expense WHERE demo_session_id = ?",
            Integer.class,
            sessionId
        );
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

    private record TestSession(
        DemoPrincipal principal,
        Authentication authentication,
        UserDataScope scope
    ) {}

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

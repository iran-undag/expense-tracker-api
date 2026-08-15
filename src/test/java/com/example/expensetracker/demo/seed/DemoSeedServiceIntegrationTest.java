package com.example.expensetracker.demo.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expensetracker.persistence.DataRealm;
import com.example.expensetracker.persistence.DataRealmExecutor;
import com.example.expensetracker.repository.BudgetRepository;
import com.example.expensetracker.repository.ExpenseCategoryRepository;
import com.example.expensetracker.repository.ExpenseRepository;
import com.example.expensetracker.repository.RecurringExpenseRepository;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
class DemoSeedServiceIntegrationTest {

    private static final String EXTERNAL_JDBC_URL = System.getProperty("demo.test.jdbc-url");
    private static final MSSQLServerContainer<?> SQL_SERVER = startSqlServerWhenNeeded();
    private static final YearMonth AUGUST_2026 = YearMonth.of(2026, 8);

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DemoSeedServiceIntegrationTest::jdbcUrl);
        registry.add("spring.datasource.username", DemoSeedServiceIntegrationTest::username);
        registry.add("spring.datasource.password", DemoSeedServiceIntegrationTest::password);
        registry.add("demo.datasource.url", DemoSeedServiceIntegrationTest::jdbcUrl);
        registry.add("demo.datasource.username", DemoSeedServiceIntegrationTest::username);
        registry.add("demo.datasource.password", DemoSeedServiceIntegrationTest::password);
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

    @Autowired private DemoDatabaseInitializer databaseInitializer;
    @Autowired private DemoSeedService seedService;
    @Autowired private DataRealmExecutor realmExecutor;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private ExpenseCategoryRepository categoryRepository;
    @Autowired private RecurringExpenseRepository recurringRepository;

    @Autowired
    @Qualifier("demoJdbcTemplate")
    private JdbcTemplate jdbc;

    @BeforeEach
    void resetDemoData() {
        databaseInitializer.ensureMigrated();
        deleteAllDemoData();
        jdbc.update("""
            INSERT INTO demo_seed_state (id, template_version, anchor_month, refreshed_at)
            VALUES (1, 0, '2026-08-01', SYSDATETIMEOFFSET())
            """);
    }

    @Test
    void installsExactDeterministicProtectedTemplate() {
        refresh(AUGUST_2026);

        assertThat(seedCount("expense_category")).isEqualTo(16);
        assertThat(jdbc.queryForList("""
            SELECT name FROM expense_category
            WHERE is_demo_seed = 1 ORDER BY name
            """, String.class)).containsExactly(
                "Electricity", "Entertainment", "Food", "Groceries", "Healthcare", "Insurance",
                "Internet", "Mortgage", "Other", "Phone", "Rent", "Shopping", "Transport",
                "Travel", "Tuition", "Water");
        assertThat(seedCount("expense")).isEqualTo(85);
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM expense
            WHERE is_demo_seed = 1 AND date >= '2026-08-01' AND date < '2026-09-01'
            """, Integer.class)).isEqualTo(25);
        assertThat(jdbc.queryForList("""
            SELECT MONTH(date) AS expense_month, COUNT(*) AS expense_count
            FROM expense
            WHERE is_demo_seed = 1 AND date >= '2026-03-01' AND date < '2026-08-01'
            GROUP BY MONTH(date) ORDER BY MONTH(date)
            """)).containsExactly(
                Map.of("expense_month", 3, "expense_count", 12),
                Map.of("expense_month", 4, "expense_count", 12),
                Map.of("expense_month", 5, "expense_count", 12),
                Map.of("expense_month", 6, "expense_count", 12),
                Map.of("expense_month", 7, "expense_count", 12));
        assertThat(seedCount("budget")).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM budget
            WHERE is_demo_seed = 1 AND budget_year = 2026 AND budget_month = 8
            """, Integer.class)).isEqualTo(5);
        assertThat(seedCount("recurring_expense")).isEqualTo(3);
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM recurring_expense
            WHERE is_demo_seed = 1 AND active = 1 AND next_run_date > '2026-08-12'
            """, Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("""
            SELECT COUNT(*) FROM recurring_expense WHERE is_demo_seed = 1 AND active = 0
            """, Integer.class)).isEqualTo(1);
        assertSeedOwnership();

        List<String> firstTemplate = logicalTemplate();
        jdbc.update("UPDATE demo_seed_state SET template_version = 0 WHERE id = 1");
        refresh(AUGUST_2026);

        assertThat(logicalTemplate()).containsExactlyElementsOf(firstTemplate);
    }

    @Test
    void leavesStaleTemplateUntouchedWhileSessionIsActive() {
        refresh(AUGUST_2026);
        List<String> augustTemplate = logicalTemplate();
        insertActiveSession();

        refresh(YearMonth.of(2026, 9));

        assertThat(logicalTemplate()).containsExactlyElementsOf(augustTemplate);
        assertThat(jdbc.queryForObject(
            "SELECT anchor_month FROM demo_seed_state WHERE id = 1",
            java.sql.Date.class).toLocalDate()).isEqualTo(AUGUST_2026.atDay(1));
    }

    @Test
    void refreshPreservesNonSeedRowsAndOwnerListQueriesExcludeOtherSessions() {
        refresh(AUGUST_2026);
        UUID ownSession = insertSession("demo:own", "11111111-1111-1111-1111-111111111111", false);
        UUID otherSession = insertSession("demo:other", "22222222-2222-2222-2222-222222222222", false);
        insertNonSeedRows("demo:own", ownSession, "Own");
        insertNonSeedRows("demo:other", otherSession, "Other visitor");

        refresh(YearMonth.of(2026, 9));

        assertThat(nonSeedCount("expense")).isEqualTo(2);
        assertThat(nonSeedCount("budget")).isEqualTo(2);
        assertThat(nonSeedCount("expense_category")).isEqualTo(2);
        assertThat(nonSeedCount("recurring_expense")).isEqualTo(2);

        realmExecutor.inRealm(DataRealm.DEMO, () -> {
            List<String> owners = List.of("demo:seed", "demo:own");
            assertThat(expenseRepository.findByUseridIn(owners))
                .extracting(expense -> expense.getUserid()).contains("demo:seed", "demo:own").doesNotContain("demo:other");
            assertThat(budgetRepository.findByUseridInOrderByBudgetYearAscBudgetMonthAscCategoryAsc(owners))
                .extracting(budget -> budget.getUserid()).contains("demo:seed", "demo:own").doesNotContain("demo:other");
            assertThat(categoryRepository.findByUseridInOrderByNameAsc(owners))
                .extracting(category -> category.getUserid()).contains("demo:seed", "demo:own").doesNotContain("demo:other");
            assertThat(recurringRepository.findByUseridInOrderByActiveDescNextRunDateAsc(owners))
                .extracting(rule -> rule.getUserid()).contains("demo:seed", "demo:own").doesNotContain("demo:other");
        });
    }

    private void refresh(YearMonth anchorMonth) {
        realmExecutor.inRealm(DataRealm.DEMO, () -> seedService.refreshIfStale(anchorMonth));
    }

    private int seedCount(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE is_demo_seed = 1", Integer.class);
    }

    private int nonSeedCount(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE is_demo_seed = 0", Integer.class);
    }

    private void assertSeedOwnership() {
        for (String table : List.of("expense", "budget", "expense_category", "recurring_expense")) {
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE is_demo_seed = 1"
                + " AND (userid <> 'demo:seed' OR demo_session_id IS NOT NULL)", Integer.class)).isZero();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_session", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM demo_access_token", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM chat_identity_mapping", Integer.class)).isZero();
    }

    private List<String> logicalTemplate() {
        return jdbc.queryForList("""
            SELECT 'expense|' + description + '|' + CONVERT(VARCHAR(20), amount) + '|'
                + CONVERT(VARCHAR(10), date, 23) + '|' + category
            FROM expense WHERE is_demo_seed = 1
            UNION ALL
            SELECT 'budget|' + category + '|' + CONVERT(VARCHAR(4), budget_year) + '|'
                + CONVERT(VARCHAR(2), budget_month) + '|' + CONVERT(VARCHAR(20), amount)
            FROM budget WHERE is_demo_seed = 1
            UNION ALL
            SELECT 'category|' + name + '|' + COALESCE(color, '') + '|' + COALESCE(icon, '')
            FROM expense_category WHERE is_demo_seed = 1
            UNION ALL
            SELECT 'recurring|' + description + '|' + CONVERT(VARCHAR(20), amount) + '|'
                + category + '|' + frequency + '|' + CONVERT(VARCHAR(10), start_date, 23) + '|'
                + CONVERT(VARCHAR(10), next_run_date, 23) + '|' + CONVERT(VARCHAR(1), active)
            FROM recurring_expense WHERE is_demo_seed = 1
            ORDER BY 1
            """, String.class);
    }

    private void insertActiveSession() {
        insertSession("demo:active", "33333333-3333-3333-3333-333333333333", true);
    }

    private UUID insertSession(String owner, String id, boolean active) {
        UUID sessionId = UUID.fromString(id);
        jdbc.update("""
            INSERT INTO demo_session
                (id, shared_account_id, persistence_owner_id, status, created_at, expires_at,
                 used_actions, reserved_actions, resume_token_digest)
            VALUES (?, 'demo-shared-account', ?, 'ACTIVE', SYSDATETIMEOFFSET(),
                DATEADD(HOUR, ?, SYSDATETIMEOFFSET()), 0, 0, ?)
            """, sessionId, owner, active ? 6 : -1, digestFor(id));
        return sessionId;
    }

    private void insertNonSeedRows(String owner, UUID sessionId, String marker) {
        jdbc.update("""
            INSERT INTO expense (description, amount, date, category, userid, demo_session_id, is_demo_seed)
            VALUES (?, 10.00, '2026-08-12', 'Other', ?, ?, 0)
            """, marker, owner, sessionId);
        jdbc.update("""
            INSERT INTO budget (userid, category, budget_year, budget_month, amount, demo_session_id, is_demo_seed)
            VALUES (?, ?, 2026, 8, 100.00, ?, 0)
            """, owner, marker, sessionId);
        jdbc.update("""
            INSERT INTO expense_category
                (userid, name, color, icon, system_default, active, demo_session_id, is_demo_seed)
            VALUES (?, ?, '#000000', 'other', 0, 1, ?, 0)
            """, owner, marker, sessionId);
        jdbc.update("""
            INSERT INTO recurring_expense
                (userid, description, amount, category, frequency, start_date, next_run_date,
                 active, demo_session_id, is_demo_seed)
            VALUES (?, ?, 10.00, 'Other', 'MONTHLY', '2026-08-01', '2026-09-01', 1, ?, 0)
            """, owner, marker, sessionId);
    }

    private String digestFor(String value) {
        return value.replace("-", "").repeat(2);
    }

    private void deleteAllDemoData() {
        jdbc.update("DELETE FROM chat_identity_mapping WHERE demo_session_id IS NOT NULL");
        jdbc.update("DELETE FROM demo_quota_reservation");
        jdbc.update("DELETE FROM recurring_expense_occurrence WHERE userid LIKE 'demo:%'");
        jdbc.update("DELETE FROM recurring_expense WHERE is_demo_seed = 1 OR demo_session_id IS NOT NULL");
        jdbc.update("DELETE FROM expense WHERE is_demo_seed = 1 OR demo_session_id IS NOT NULL");
        jdbc.update("DELETE FROM budget WHERE is_demo_seed = 1 OR demo_session_id IS NOT NULL");
        jdbc.update("DELETE FROM expense_category WHERE is_demo_seed = 1 OR demo_session_id IS NOT NULL");
        jdbc.update("DELETE FROM demo_access_token");
        jdbc.update("DELETE FROM demo_session");
        jdbc.update("DELETE FROM demo_session_admission");
        jdbc.update("DELETE FROM demo_seed_state");
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
            ? System.getProperty("demo.test.password", "Task5-SqlServer-Password1!")
            : SQL_SERVER.getPassword();
    }
}

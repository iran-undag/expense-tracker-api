package com.example.expensetracker.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class DemoSchemaMigrationTest {

    @Container
    private static final MSSQLServerContainer<?> SQL_SERVER =
        new MSSQLServerContainer<>("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense();

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
            .dataSource(SQL_SERVER.getJdbcUrl(), SQL_SERVER.getUsername(), SQL_SERVER.getPassword())
            .locations("classpath:db/migration")
            .load()
            .migrate();
    }

    @Test
    void migrationCreatesDemoSessionTablesAndBusinessOwnershipColumns() throws SQLException {
        assertThat(columns("demo_session"))
            .contains("id", "expires_at", "used_actions", "reserved_actions");
        assertThat(columns("demo_session_attempt")).contains("ip_digest", "attempted_at");
        assertThat(columns("expense")).contains("demo_session_id", "is_demo_seed");
        assertThat(columns("budget")).contains("demo_session_id", "is_demo_seed");
        assertThat(columns("expense_category")).contains("demo_session_id", "is_demo_seed");
        assertThat(columns("recurring_expense")).contains("demo_session_id", "is_demo_seed");
    }

    @Test
    void migrationEnforcesFifteenActionSessionAndReservationLimits() throws SQLException {
        try (Connection connection = SQL_SERVER.createConnection("");
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                INSERT INTO demo_session
                    (id, shared_account_id, persistence_owner_id, status, created_at, expires_at,
                     used_actions, reserved_actions, resume_token_digest)
                VALUES
                    ('11111111-1111-1111-1111-111111111111', 'shared', 'demo:limit-15', 'ACTIVE',
                     SYSDATETIMEOFFSET(), DATEADD(HOUR, 1, SYSDATETIMEOFFSET()), 15, 0,
                     REPLICATE('a', 64))
                """);

            assertThatThrownBy(() -> statement.executeUpdate("""
                INSERT INTO demo_session
                    (id, shared_account_id, persistence_owner_id, status, created_at, expires_at,
                     used_actions, reserved_actions, resume_token_digest)
                VALUES
                    ('22222222-2222-2222-2222-222222222222', 'shared', 'demo:limit-16', 'ACTIVE',
                     SYSDATETIMEOFFSET(), DATEADD(HOUR, 1, SYSDATETIMEOFFSET()), 16, 0,
                     REPLICATE('b', 64))
                """)).isInstanceOf(SQLException.class);

            assertThatThrownBy(() -> statement.executeUpdate("""
                INSERT INTO demo_quota_reservation
                    (id, demo_session_id, cost, state, created_at, expires_at)
                VALUES
                    ('33333333-3333-3333-3333-333333333333',
                     '11111111-1111-1111-1111-111111111111', 16, 'PENDING',
                     SYSDATETIMEOFFSET(), DATEADD(MINUTE, 5, SYSDATETIMEOFFSET()))
                """)).isInstanceOf(SQLException.class);
        }
    }

    private Set<String> columns(String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Connection connection = SQL_SERVER.createConnection("");
             ResultSet resultSet = metadataColumns(connection.getMetaData(), tableName)) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("COLUMN_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return columns;
    }

    private ResultSet metadataColumns(DatabaseMetaData metadata, String tableName) throws SQLException {
        return metadata.getColumns(null, "dbo", tableName, null);
    }
}

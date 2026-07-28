package com.example.expensetracker.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prod-config;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("prod")
class ProductionDataSourceConfigTest {

    @Autowired
    private HikariDataSource dataSource;

    @Test
    void productionDataSource_shouldReleaseIdleConnectionsAndRetryColdStarts() {
        assertThat(dataSource.getMinimumIdle()).isZero();
        assertThat(dataSource.getIdleTimeout()).isEqualTo(300_000);
        assertThat(dataSource.getDataSourceProperties())
                .containsEntry("loginTimeout", "120")
                .containsEntry("connectRetryCount", "5")
                .containsEntry("connectRetryInterval", "15");
    }
}

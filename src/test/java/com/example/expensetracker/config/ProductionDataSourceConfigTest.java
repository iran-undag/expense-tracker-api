package com.example.expensetracker.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:prod-config;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "demo.datasource.url=jdbc:h2:mem:prod-demo-config;DB_CLOSE_DELAY=-1",
        "demo.datasource.username=sa",
        "demo.datasource.password=",
        "demo.datasource.driver-class-name=org.h2.Driver",
        "demo.datasource.hikari.minimum-idle=0",
        "demo.token-hmac-key=0123456789abcdef0123456789abcdef",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.flyway.enabled=false"
})
@ActiveProfiles("prod")
class ProductionDataSourceConfigTest {

    @Autowired
    @Qualifier("primaryDataSource")
    private HikariDataSource primaryDataSource;

    @Autowired
    @Qualifier("demoDataSource")
    private HikariDataSource demoDataSource;

    @Test
    void productionDataSource_shouldReleaseIdleConnectionsAndRetryColdStarts() {
        assertThat(primaryDataSource.getMinimumIdle()).isZero();
        assertThat(primaryDataSource.getIdleTimeout()).isEqualTo(300_000);
        assertThat(primaryDataSource.getDataSourceProperties())
                .containsEntry("loginTimeout", "120")
                .containsEntry("connectRetryCount", "5")
                .containsEntry("connectRetryInterval", "15");
        assertThat(demoDataSource.getMinimumIdle()).isZero();
        assertThat(demoDataSource.isRunning()).isFalse();
    }
}

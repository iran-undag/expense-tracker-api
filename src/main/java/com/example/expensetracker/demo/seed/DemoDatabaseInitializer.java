package com.example.expensetracker.demo.seed;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class DemoDatabaseInitializer {
    private final DataSource demoDataSource;
    private boolean migrated;

    public DemoDatabaseInitializer(@Qualifier("demoDataSource") DataSource demoDataSource) {
        this.demoDataSource = demoDataSource;
    }

    public synchronized void ensureMigrated() {
        if (migrated) {
            return;
        }
        Flyway.configure()
            .dataSource(demoDataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();
        migrated = true;
    }
}

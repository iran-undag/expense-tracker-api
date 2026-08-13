package com.example.expensetracker.config;

import com.example.expensetracker.persistence.DataRealm;
import com.example.expensetracker.persistence.RealmRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@Profile("prod")
public class DataSourceConfig {
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    DataSourceProperties primaryDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("demo.datasource")
    DataSourceProperties demoDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean(name = "primaryDataSource")
    @ConfigurationProperties("spring.datasource.hikari")
    HikariDataSource primaryDataSource(@Qualifier("primaryDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean(name = "demoDataSource")
    @ConfigurationProperties("demo.datasource.hikari")
    HikariDataSource demoDataSource(@Qualifier("demoDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
    }

    @Bean
    @Primary
    DataSource dataSource(
        @Qualifier("primaryDataSource") DataSource primaryDataSource,
        @Qualifier("demoDataSource") DataSource demoDataSource
    ) {
        RealmRoutingDataSource routingDataSource = new RealmRoutingDataSource();
        routingDataSource.setTargetDataSources(Map.of(
            DataRealm.PRIMARY, primaryDataSource,
            DataRealm.DEMO, demoDataSource
        ));
        routingDataSource.setDefaultTargetDataSource(primaryDataSource);
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }

    @Bean
    JdbcTemplate demoJdbcTemplate(@Qualifier("demoDataSource") DataSource demoDataSource) {
        return new JdbcTemplate(demoDataSource);
    }
}

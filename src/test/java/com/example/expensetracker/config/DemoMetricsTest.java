package com.example.expensetracker.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class DemoMetricsTest {

    @Test
    void recordsOnlyFixedSessionAndQuotaDimensions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DemoMetrics metrics = new DemoMetrics(registry);

        metrics.sessionCreated();
        metrics.sessionResumed();
        metrics.sessionLoggedOut();
        metrics.capacityRejected();
        metrics.quotaRejected(DemoMetrics.Operation.EXTERNAL);
        metrics.cleanedSessions(2);
        metrics.databaseFailure();
        metrics.activeSessions(2);

        assertThat(registry.get("demo.sessions.events").tag("event", "created").counter().count())
            .isEqualTo(1);
        assertThat(registry.get("demo.sessions.events").tag("event", "resumed").counter().count())
            .isEqualTo(1);
        assertThat(registry.get("demo.sessions.events").tag("event", "logout").counter().count())
            .isEqualTo(1);
        assertThat(registry.get("demo.sessions.events").tag("event", "capacity_rejected")
            .counter().count()).isEqualTo(1);
        assertThat(registry.get("demo.quota.rejections").tag("operation", "external")
            .counter().count()).isEqualTo(1);
        assertThat(registry.get("demo.cleanup.sessions").counter().count()).isEqualTo(2);
        assertThat(registry.get("demo.database.failures").counter().count()).isEqualTo(1);
        assertThat(registry.get("demo.sessions.active").gauge().value()).isEqualTo(2);
    }
}

package com.example.expensetracker.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class DemoMetrics {

    public enum Operation {
        MUTATION,
        EXTERNAL,
        RECURRING
    }

    private final Map<SessionEvent, Counter> sessionEvents;
    private final Map<Operation, Counter> quotaRejections;
    private final Counter cleanedSessions;
    private final Counter databaseFailures;
    private final AtomicInteger activeSessions = new AtomicInteger();

    public DemoMetrics(MeterRegistry registry) {
        sessionEvents = new EnumMap<>(SessionEvent.class);
        for (SessionEvent event : SessionEvent.values()) {
            sessionEvents.put(event, Counter.builder("demo.sessions.events")
                .tag("event", event.tag)
                .register(registry));
        }
        quotaRejections = new EnumMap<>(Operation.class);
        for (Operation operation : Operation.values()) {
            quotaRejections.put(operation, Counter.builder("demo.quota.rejections")
                .tag("operation", operation.name().toLowerCase(java.util.Locale.ROOT))
                .register(registry));
        }
        cleanedSessions = registry.counter("demo.cleanup.sessions");
        databaseFailures = registry.counter("demo.database.failures");
        registry.gauge("demo.sessions.active", activeSessions);
    }

    public void sessionCreated() {
        sessionEvents.get(SessionEvent.CREATED).increment();
    }

    public void sessionResumed() {
        sessionEvents.get(SessionEvent.RESUMED).increment();
    }

    public void sessionLoggedOut() {
        sessionEvents.get(SessionEvent.LOGOUT).increment();
    }

    public void capacityRejected() {
        sessionEvents.get(SessionEvent.CAPACITY_REJECTED).increment();
    }

    public void quotaRejected(Operation operation) {
        quotaRejections.get(operation).increment();
    }

    public void cleanedSessions(int count) {
        if (count > 0) {
            cleanedSessions.increment(count);
        }
    }

    public void databaseFailure() {
        databaseFailures.increment();
    }

    public void activeSessions(int count) {
        activeSessions.set(Math.max(0, count));
    }

    private enum SessionEvent {
        CREATED("created"),
        RESUMED("resumed"),
        LOGOUT("logout"),
        CAPACITY_REJECTED("capacity_rejected");

        private final String tag;

        SessionEvent(String tag) {
            this.tag = tag;
        }
    }
}

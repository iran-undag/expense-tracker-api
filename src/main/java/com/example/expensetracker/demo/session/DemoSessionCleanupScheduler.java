package com.example.expensetracker.demo.session;

import com.example.expensetracker.config.DemoMetrics;
import com.example.expensetracker.persistence.DataRealm;
import com.example.expensetracker.persistence.DataRealmExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class DemoSessionCleanupScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoSessionCleanupScheduler.class);

    private final TaskExecutor taskExecutor;
    private final DataRealmExecutor realmExecutor;
    private final DemoSessionCleanupService cleanupService;
    private final DemoMetrics metrics;
    private final AtomicBoolean running = new AtomicBoolean();

    public DemoSessionCleanupScheduler(
        @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
        DataRealmExecutor realmExecutor,
        DemoSessionCleanupService cleanupService,
        DemoMetrics metrics
    ) {
        this.taskExecutor = taskExecutor;
        this.realmExecutor = realmExecutor;
        this.cleanupService = cleanupService;
        this.metrics = metrics;
    }

    public void schedule() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        try {
            taskExecutor.execute(this::runCleanup);
        } catch (RuntimeException exception) {
            running.set(false);
            metrics.databaseFailure();
            LOGGER.error("Unable to schedule demo session cleanup", exception);
        }
    }

    private void runCleanup() {
        try {
            realmExecutor.inRealm(DataRealm.DEMO, cleanupService::cleanupExpiredSessions);
        } catch (RuntimeException exception) {
            metrics.databaseFailure();
            LOGGER.error("Demo session cleanup failed", exception);
        } finally {
            running.set(false);
        }
    }
}

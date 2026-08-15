package com.example.expensetracker.demo.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.expensetracker.config.DemoMetrics;
import com.example.expensetracker.persistence.DataRealmExecutor;
import java.util.ArrayDeque;
import java.util.Queue;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;

class DemoSessionCleanupSchedulerTest {

    @Test
    void runsCleanupAsynchronouslyAndSingleFlight() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        TaskExecutor executor = tasks::add;
        DataRealmExecutor realmExecutor = new DataRealmExecutor();
        DemoSessionCleanupService cleanupService = mock(DemoSessionCleanupService.class);
        DemoMetrics metrics = mock(DemoMetrics.class);
        DemoSessionCleanupScheduler scheduler = new DemoSessionCleanupScheduler(
            executor, realmExecutor, cleanupService, metrics);

        scheduler.schedule();
        scheduler.schedule();

        assertThat(tasks).hasSize(1);
        verify(cleanupService, never()).cleanupExpiredSessions();

        tasks.remove().run();
        verify(cleanupService).cleanupExpiredSessions();

        scheduler.schedule();
        assertThat(tasks).hasSize(1);
    }

    @Test
    void reportsFailureAndAllowsLaterRetry() {
        Queue<Runnable> tasks = new ArrayDeque<>();
        DemoSessionCleanupService cleanupService = mock(DemoSessionCleanupService.class);
        DemoMetrics metrics = mock(DemoMetrics.class);
        DemoSessionCleanupScheduler scheduler = new DemoSessionCleanupScheduler(
            tasks::add, new DataRealmExecutor(), cleanupService, metrics);
        doThrow(new IllegalStateException("database unavailable"))
            .when(cleanupService).cleanupExpiredSessions();

        scheduler.schedule();
        tasks.remove().run();
        scheduler.schedule();

        verify(metrics).databaseFailure();
        assertThat(tasks).hasSize(1);
        verify(cleanupService, times(1)).cleanupExpiredSessions();
    }
}

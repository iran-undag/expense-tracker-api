package com.example.expensetracker.demo.session;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expensetracker.config.DemoMetrics;
import org.junit.jupiter.api.Test;

class DemoSessionCleanupServiceTest {

    @Test
    void recordsDeletedSessions() {
        DemoSessionRepository sessionRepository = mock(DemoSessionRepository.class);
        DemoMetrics metrics = mock(DemoMetrics.class);
        DemoSessionCleanupService service = new DemoSessionCleanupService(sessionRepository, metrics);
        when(sessionRepository.deleteExpiredData()).thenReturn(3);

        service.cleanupExpiredSessions();

        verify(metrics).cleanedSessions(3);
    }
}

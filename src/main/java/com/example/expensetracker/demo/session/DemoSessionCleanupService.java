package com.example.expensetracker.demo.session;

import com.example.expensetracker.config.DemoMetrics;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("prod")
public class DemoSessionCleanupService {

    private final DemoSessionRepository sessionRepository;
    private final DemoMetrics metrics;

    public DemoSessionCleanupService(
        DemoSessionRepository sessionRepository,
        DemoMetrics metrics
    ) {
        this.sessionRepository = sessionRepository;
        this.metrics = metrics;
    }

    @Transactional
    public void cleanupExpiredSessions() {
        metrics.cleanedSessions(sessionRepository.deleteExpiredData());
    }
}

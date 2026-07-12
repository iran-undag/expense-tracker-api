package com.example.expensetracker.chattool;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class ChatToolRateLimiterTest {
    @Test
    void rejectsRequestsAboveLimitWithinMinute() {
        ChatToolRateLimiter limiter = new ChatToolRateLimiter(
            2, Clock.fixed(Instant.parse("2026-07-12T00:00:00Z"), ZoneOffset.UTC));

        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isTrue();
        assertThat(limiter.tryAcquire()).isFalse();
    }
}

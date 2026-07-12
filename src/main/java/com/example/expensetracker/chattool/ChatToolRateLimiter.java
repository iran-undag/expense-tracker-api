package com.example.expensetracker.chattool;

import java.time.Clock;

public class ChatToolRateLimiter {
    private final int limit;
    private final Clock clock;
    private long minute = Long.MIN_VALUE;
    private int count;

    public ChatToolRateLimiter(int limit, Clock clock) {
        if (limit < 1) throw new IllegalArgumentException("Rate limit must be positive");
        this.limit = limit;
        this.clock = clock;
    }

    public synchronized boolean tryAcquire() {
        long currentMinute = clock.instant().getEpochSecond() / 60;
        if (currentMinute != minute) {
            minute = currentMinute;
            count = 0;
        }
        if (count >= limit) return false;
        count++;
        return true;
    }
}

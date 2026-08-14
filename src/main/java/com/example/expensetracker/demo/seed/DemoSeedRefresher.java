package com.example.expensetracker.demo.seed;

import java.time.YearMonth;

public interface DemoSeedRefresher {
    void refreshIfStale(YearMonth anchorMonth);
}

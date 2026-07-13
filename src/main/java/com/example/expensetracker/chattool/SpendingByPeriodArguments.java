package com.example.expensetracker.chattool;

import com.example.expensetracker.service.SpendingGranularity;
import java.time.LocalDate;

public record SpendingByPeriodArguments(
    LocalDate fromDate,
    LocalDate toDate,
    SpendingGranularity granularity,
    String category
) implements ChatToolArguments {
}

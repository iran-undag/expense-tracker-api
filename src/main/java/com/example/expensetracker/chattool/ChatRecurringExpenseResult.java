package com.example.expensetracker.chattool;

import com.example.expensetracker.model.RecurringFrequency;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ChatRecurringExpenseResult(
    String description,
    BigDecimal amount,
    String category,
    RecurringFrequency frequency,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate nextRunDate,
    boolean active
) {
}

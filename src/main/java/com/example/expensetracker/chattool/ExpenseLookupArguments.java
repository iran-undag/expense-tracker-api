package com.example.expensetracker.chattool;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseLookupArguments(
    LocalDate fromDate,
    LocalDate toDate,
    String category,
    String query,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    int page,
    int size
) implements ChatToolArguments {
}

package com.example.expensetracker.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpendingPeriodDto(
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal totalAmount,
    long expenseCount
) {
}

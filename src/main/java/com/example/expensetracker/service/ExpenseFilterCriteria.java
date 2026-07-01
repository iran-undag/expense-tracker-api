package com.example.expensetracker.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ExpenseFilterCriteria(
    LocalDate fromDate,
    LocalDate toDate,
    String category,
    BigDecimal minAmount,
    BigDecimal maxAmount,
    String query
) {}

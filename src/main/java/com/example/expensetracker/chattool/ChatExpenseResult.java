package com.example.expensetracker.chattool;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ChatExpenseResult(Long id, String description, BigDecimal amount, LocalDate date, String category) {
}

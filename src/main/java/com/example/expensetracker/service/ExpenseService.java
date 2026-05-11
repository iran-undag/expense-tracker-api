package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseService {
    Expense saveExpense(Expense expense);
    Optional<Expense> getExpenseById(Long id);
    List<Expense> getExpensesByDate(LocalDate date);
    BigDecimal getTotalExpensesForMonth(int year, int month);
    List<Expense> getAllExpenses();
}

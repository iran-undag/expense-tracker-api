package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseService {
    Expense saveExpense(Expense expense);
    Optional<Expense> getExpenseById(Long id, String userId);
    List<Expense> getExpensesByDate(LocalDate date, String userId);
    BigDecimal getTotalExpensesForMonth(int year, int month, String userId);
    List<Expense> getAllExpenses(String userId);
    Expense updateExpense(Long id, String userId, Expense expense);
    void deleteExpense(Long id, String userId);
}

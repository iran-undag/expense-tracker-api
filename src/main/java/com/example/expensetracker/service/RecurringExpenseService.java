package com.example.expensetracker.service;

import com.example.expensetracker.model.RecurringExpense;
import java.time.LocalDate;
import java.util.List;

public interface RecurringExpenseService {
    RecurringExpense saveRecurringExpense(String userId, RecurringExpense recurringExpense);
    List<RecurringExpense> getRecurringExpenses(String userId);
    RecurringExpense updateRecurringExpense(Long id, String userId, RecurringExpense recurringExpense);
    void deleteRecurringExpense(Long id, String userId);
    int generateDueExpenses(String userId, LocalDate today);
}

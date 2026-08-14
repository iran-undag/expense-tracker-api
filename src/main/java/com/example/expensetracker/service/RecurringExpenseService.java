package com.example.expensetracker.service;

import com.example.expensetracker.model.RecurringExpense;
import com.example.expensetracker.security.UserDataScope;
import java.time.LocalDate;
import java.util.List;

public interface RecurringExpenseService {
    RecurringExpense saveRecurringExpense(UserDataScope scope, RecurringExpense recurringExpense);
    List<RecurringExpense> getRecurringExpenses(UserDataScope scope);
    RecurringExpense updateRecurringExpense(Long id, UserDataScope scope, RecurringExpense recurringExpense);
    void deleteRecurringExpense(Long id, UserDataScope scope);
    int generateDueExpenses(UserDataScope scope, LocalDate today);

    default RecurringExpense saveRecurringExpense(String userId, RecurringExpense recurringExpense) { return saveRecurringExpense(UserDataScope.personal(userId), recurringExpense); }
    default List<RecurringExpense> getRecurringExpenses(String userId) { return getRecurringExpenses(UserDataScope.personal(userId)); }
    default RecurringExpense updateRecurringExpense(Long id, String userId, RecurringExpense recurringExpense) { return updateRecurringExpense(id, UserDataScope.personal(userId), recurringExpense); }
    default void deleteRecurringExpense(Long id, String userId) { deleteRecurringExpense(id, UserDataScope.personal(userId)); }
    default int generateDueExpenses(String userId, LocalDate today) { return generateDueExpenses(UserDataScope.personal(userId), today); }
}

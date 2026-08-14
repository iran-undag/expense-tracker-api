package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;
import com.example.expensetracker.security.UserDataScope;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ExpenseService {
    Expense saveExpense(UserDataScope scope, Expense expense);
    Optional<Expense> getExpenseById(Long id, UserDataScope scope);
    List<Expense> getExpensesByDate(LocalDate date, UserDataScope scope);
    BigDecimal getTotalExpensesForMonth(int year, int month, UserDataScope scope);
    Page<Expense> getAllExpenses(UserDataScope scope, ExpenseFilterCriteria filters, Pageable pageable);
    Page<Expense> getExpensesForMonth(int year, int month, UserDataScope scope, Pageable pageable);
    Expense updateExpense(Long id, UserDataScope scope, Expense expense);
    void deleteExpense(Long id, UserDataScope scope);

    default Expense saveExpense(Expense expense) {
        return saveExpense(UserDataScope.personal(expense.getUserid()), expense);
    }
    default Optional<Expense> getExpenseById(Long id, String userId) {
        return getExpenseById(id, UserDataScope.personal(userId));
    }
    default List<Expense> getExpensesByDate(LocalDate date, String userId) {
        return getExpensesByDate(date, UserDataScope.personal(userId));
    }
    default BigDecimal getTotalExpensesForMonth(int year, int month, String userId) {
        return getTotalExpensesForMonth(year, month, UserDataScope.personal(userId));
    }
    default Page<Expense> getAllExpenses(String userId, ExpenseFilterCriteria filters, Pageable pageable) {
        return getAllExpenses(UserDataScope.personal(userId), filters, pageable);
    }
    default Page<Expense> getExpensesForMonth(int year, int month, String userId, Pageable pageable) {
        return getExpensesForMonth(year, month, UserDataScope.personal(userId), pageable);
    }
    default Expense updateExpense(Long id, String userId, Expense expense) {
        return updateExpense(id, UserDataScope.personal(userId), expense);
    }
    default void deleteExpense(Long id, String userId) {
        deleteExpense(id, UserDataScope.personal(userId));
    }
}

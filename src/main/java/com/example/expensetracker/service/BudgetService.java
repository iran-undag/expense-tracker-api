package com.example.expensetracker.service;

import com.example.expensetracker.dto.BudgetSummaryDto;
import com.example.expensetracker.model.Budget;
import com.example.expensetracker.security.UserDataScope;
import java.util.List;
import java.util.Optional;

public interface BudgetService {
    Budget saveBudget(UserDataScope scope, Budget budget);
    List<Budget> getBudgets(UserDataScope scope, int year, int month);
    List<BudgetSummaryDto> getBudgetSummary(UserDataScope scope, int year, int month);
    Optional<Budget> getBudgetById(Long id, UserDataScope scope);
    Budget updateBudget(Long id, UserDataScope scope, Budget budget);
    void deleteBudget(Long id, UserDataScope scope);

    default Budget saveBudget(String userId, Budget budget) { return saveBudget(UserDataScope.personal(userId), budget); }
    default List<Budget> getBudgets(String userId, int year, int month) { return getBudgets(UserDataScope.personal(userId), year, month); }
    default List<BudgetSummaryDto> getBudgetSummary(String userId, int year, int month) { return getBudgetSummary(UserDataScope.personal(userId), year, month); }
    default Optional<Budget> getBudgetById(Long id, String userId) { return getBudgetById(id, UserDataScope.personal(userId)); }
    default Budget updateBudget(Long id, String userId, Budget budget) { return updateBudget(id, UserDataScope.personal(userId), budget); }
    default void deleteBudget(Long id, String userId) { deleteBudget(id, UserDataScope.personal(userId)); }
}

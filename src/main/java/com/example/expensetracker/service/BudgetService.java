package com.example.expensetracker.service;

import com.example.expensetracker.dto.BudgetSummaryDto;
import com.example.expensetracker.model.Budget;
import java.util.List;
import java.util.Optional;

public interface BudgetService {
    Budget saveBudget(String userId, Budget budget);
    List<Budget> getBudgets(String userId, int year, int month);
    List<BudgetSummaryDto> getBudgetSummary(String userId, int year, int month);
    Optional<Budget> getBudgetById(Long id, String userId);
    Budget updateBudget(Long id, String userId, Budget budget);
    void deleteBudget(Long id, String userId);
}

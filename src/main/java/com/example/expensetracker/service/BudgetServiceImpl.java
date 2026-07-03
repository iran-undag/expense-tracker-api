package com.example.expensetracker.service;

import com.example.expensetracker.dto.BudgetSummaryDto;
import com.example.expensetracker.dto.CategoryTotalDto;
import com.example.expensetracker.model.Budget;
import com.example.expensetracker.repository.BudgetRepository;
import com.example.expensetracker.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BudgetServiceImpl implements BudgetService {

    private final BudgetRepository budgetRepository;
    private final ExpenseRepository expenseRepository;

    @Override
    @Transactional
    public Budget saveBudget(String userId, Budget budget) {
        validateMonth(budget.getBudgetMonth());
        String category = normalizeCategory(budget.getCategory());
        return budgetRepository
            .findByUseridAndBudgetYearAndBudgetMonthAndCategoryIgnoreCase(
                userId,
                budget.getBudgetYear(),
                budget.getBudgetMonth(),
                category
            )
            .map(existing -> {
                existing.setAmount(budget.getAmount());
                return budgetRepository.save(existing);
            })
            .orElseGet(() -> {
                budget.setUserid(userId);
                budget.setCategory(category);
                return budgetRepository.save(budget);
            });
    }

    @Override
    public List<Budget> getBudgets(String userId, int year, int month) {
        validateMonth(month);
        Map<String, Budget> latestByCategory = new LinkedHashMap<>();
        for (Budget budget : budgetRepository.findEffectiveCandidates(userId, year, month)) {
            latestByCategory.put(normalizeCategory(budget.getCategory()).toLowerCase(), budget);
        }
        return latestByCategory.values().stream()
            .sorted(Comparator.comparing(Budget::getCategory, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @Override
    public List<BudgetSummaryDto> getBudgetSummary(String userId, int year, int month) {
        validateMonth(month);
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        Map<String, BigDecimal> actualsByCategory = new LinkedHashMap<>();
        for (CategoryTotalDto total : expenseRepository.sumByCategoryForUserAndDateBetween(userId, startDate, endDate)) {
            actualsByCategory.put(normalizeCategory(total.category()), nullToZero(total.total()));
        }

        Map<String, Budget> budgetsByCategory = new LinkedHashMap<>();
        for (Budget budget : getBudgets(userId, year, month)) {
            budgetsByCategory.put(normalizeCategory(budget.getCategory()), budget);
        }

        List<String> categories = new ArrayList<>();
        categories.addAll(budgetsByCategory.keySet());
        for (String category : actualsByCategory.keySet()) {
            if (!categories.contains(category)) {
                categories.add(category);
            }
        }

        return categories.stream()
            .sorted(Comparator.naturalOrder())
            .map(category -> {
                Budget budget = budgetsByCategory.get(category);
                BigDecimal budgetAmount = budget == null ? BigDecimal.ZERO : nullToZero(budget.getAmount());
                BigDecimal actualAmount = actualsByCategory.getOrDefault(category, BigDecimal.ZERO);
                BigDecimal remainingAmount = budgetAmount.subtract(actualAmount);
                return BudgetSummaryDto.builder()
                    .category(category)
                    .year(year)
                    .month(month)
                    .budgetAmount(budgetAmount)
                    .actualAmount(actualAmount)
                    .remainingAmount(remainingAmount)
                    .percentUsed(percentUsed(actualAmount, budgetAmount))
                    .overBudget(budgetAmount.signum() > 0 && actualAmount.compareTo(budgetAmount) > 0)
                    .build();
            })
            .toList();
    }

    @Override
    public java.util.Optional<Budget> getBudgetById(Long id, String userId) {
        return budgetRepository.findByIdAndUserid(id, userId);
    }

    @Override
    @Transactional
    public Budget updateBudget(Long id, String userId, Budget budget) {
        validateMonth(budget.getBudgetMonth());
        return budgetRepository.findByIdAndUserid(id, userId)
            .map(existing -> {
                existing.setCategory(normalizeCategory(budget.getCategory()));
                existing.setBudgetYear(budget.getBudgetYear());
                existing.setBudgetMonth(budget.getBudgetMonth());
                existing.setAmount(budget.getAmount());
                return budgetRepository.save(existing);
            })
            .orElseThrow(() -> new RuntimeException("Budget not found or you do not have permission to update it"));
    }

    @Override
    @Transactional
    public void deleteBudget(Long id, String userId) {
        Budget budget = budgetRepository.findByIdAndUserid(id, userId)
            .orElseThrow(() -> new RuntimeException("Budget not found or you do not have permission to delete it"));
        budgetRepository.delete(budget);
    }

    private void validateMonth(Integer month) {
        if (month == null || month < 1 || month > 12) {
            throw new IllegalArgumentException("Month must be between 1 and 12");
        }
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "Other";
        }
        return category.trim();
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal percentUsed(BigDecimal actualAmount, BigDecimal budgetAmount) {
        if (budgetAmount.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return actualAmount
            .multiply(BigDecimal.valueOf(100))
            .divide(budgetAmount, 2, RoundingMode.HALF_UP);
    }
}

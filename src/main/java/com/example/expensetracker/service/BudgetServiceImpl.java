package com.example.expensetracker.service;

import com.example.expensetracker.dto.BudgetSummaryDto;
import com.example.expensetracker.dto.CategoryTotalDto;
import com.example.expensetracker.model.Budget;
import com.example.expensetracker.repository.BudgetRepository;
import com.example.expensetracker.repository.ExpenseRepository;
import com.example.expensetracker.security.UserDataScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
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
    public Budget saveBudget(UserDataScope scope, Budget budget) {
        validateMonth(budget.getBudgetMonth());
        String category = normalizeCategory(budget.getCategory());
        return budgetRepository
            .findByUseridAndBudgetYearAndBudgetMonthAndCategoryIgnoreCase(
                scope.ownerId(),
                budget.getBudgetYear(),
                budget.getBudgetMonth(),
                category
            )
            .map(existing -> {
                existing.setAmount(budget.getAmount());
                return budgetRepository.save(existing);
            })
            .orElseGet(() -> {
                budget.setUserid(scope.ownerId());
                budget.setCategory(category);
                budget.setDemoSessionId(scope.demoSessionId());
                budget.setDemoSeed(false);
                return budgetRepository.save(budget);
            });
    }

    @Override
    public List<Budget> getBudgets(UserDataScope scope, int year, int month) {
        validateMonth(month);
        Map<String, Budget> overlaidCandidates = new LinkedHashMap<>();
        List<Budget> candidates = budgetRepository.findEffectiveCandidatesForOwners(scope.readableOwnerIds(), year, month);
        candidates.stream().filter(Budget::isDemoSeed).forEach(budget ->
            overlaidCandidates.put(naturalKey(budget), budget));
        candidates.stream().filter(budget -> !budget.isDemoSeed()).forEach(budget ->
            overlaidCandidates.put(naturalKey(budget), budget));

        Map<String, Budget> latestByCategory = new LinkedHashMap<>();
        for (Budget budget : overlaidCandidates.values().stream()
            .sorted(Comparator.comparing(Budget::getBudgetYear).thenComparing(Budget::getBudgetMonth))
            .toList()) {
            latestByCategory.put(normalizeCategory(budget.getCategory()).toLowerCase(), budget);
        }
        return latestByCategory.values().stream()
            .sorted(Comparator.comparing(Budget::getCategory, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    @Override
    public List<BudgetSummaryDto> getBudgetSummary(UserDataScope scope, int year, int month) {
        validateMonth(month);
        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        Map<String, BigDecimal> actualsByCategory = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (CategoryTotalDto total : expenseRepository.sumByCategoryForOwnersAndDateBetween(scope.readableOwnerIds(), startDate, endDate)) {
            actualsByCategory.put(normalizeCategory(total.category()), nullToZero(total.total()));
        }

        Map<String, Budget> budgetsByCategory = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Budget budget : getBudgets(scope, year, month)) {
            budgetsByCategory.put(normalizeCategory(budget.getCategory()), budget);
        }

        Set<String> categories = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        categories.addAll(budgetsByCategory.keySet());
        categories.addAll(actualsByCategory.keySet());

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
    public java.util.Optional<Budget> getBudgetById(Long id, UserDataScope scope) {
        return budgetRepository.findByIdAndUseridIn(id, scope.readableOwnerIds());
    }

    @Override
    @Transactional
    public Budget updateBudget(Long id, UserDataScope scope, Budget budget) {
        validateMonth(budget.getBudgetMonth());
        return budgetRepository.findByIdAndUserid(id, scope.ownerId())
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
    public void deleteBudget(Long id, UserDataScope scope) {
        Budget budget = budgetRepository.findByIdAndUserid(id, scope.ownerId())
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

    private String naturalKey(Budget budget) {
        return budget.getBudgetYear() + "|" + budget.getBudgetMonth() + "|"
            + normalizeCategory(budget.getCategory()).toLowerCase();
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

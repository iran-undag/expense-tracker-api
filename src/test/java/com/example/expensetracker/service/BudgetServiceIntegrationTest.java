package com.example.expensetracker.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expensetracker.dto.BudgetSummaryDto;
import com.example.expensetracker.model.Budget;
import com.example.expensetracker.model.Expense;
import com.example.expensetracker.repository.BudgetRepository;
import com.example.expensetracker.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class BudgetServiceIntegrationTest {

    @Autowired
    private BudgetService budgetService;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @BeforeEach
    void setUp() {
        budgetRepository.deleteAll();
        expenseRepository.deleteAll();
    }

    @Test
    void saveBudget_shouldUpsertByUserMonthAndCategory() {
        Budget created = budgetService.saveBudget("testuser", budget(" Food ", "500.00", 2026, 6));
        Budget updated = budgetService.saveBudget("testuser", budget("food", "650.00", 2026, 6));

        assertThat(updated.getId()).isEqualTo(created.getId());
        assertThat(updated.getAmount()).isEqualByComparingTo("650.00");
        assertThat(budgetRepository.findAll()).hasSize(1);
    }

    @Test
    void getBudgetSummary_shouldCombineBudgetsAndActualsForAuthenticatedUserOnly() {
        budgetRepository.saveAll(List.of(
            budget("testuser", "Food", "500.00", 2026, 6),
            budget("testuser", "Transport", "100.00", 2026, 6),
            budget("otheruser", "Food", "999.00", 2026, 6)
        ));
        expenseRepository.saveAll(List.of(
            expense("testuser", "Lunch", "Food", "150.00", LocalDate.of(2026, 6, 10)),
            expense("testuser", "Dinner", "Food", "400.00", LocalDate.of(2026, 6, 12)),
            expense("testuser", "Movie", "Entertainment", "80.00", LocalDate.of(2026, 6, 15)),
            expense("testuser", "July food", "Food", "1000.00", LocalDate.of(2026, 7, 1)),
            expense("otheruser", "Lunch", "Food", "999.00", LocalDate.of(2026, 6, 10))
        ));

        List<BudgetSummaryDto> summary = budgetService.getBudgetSummary("testuser", 2026, 6);

        BudgetSummaryDto food = find(summary, "Food");
        assertThat(food.getBudgetAmount()).isEqualByComparingTo("500.00");
        assertThat(food.getActualAmount()).isEqualByComparingTo("550.00");
        assertThat(food.getRemainingAmount()).isEqualByComparingTo("-50.00");
        assertThat(food.getPercentUsed()).isEqualByComparingTo("110.00");
        assertThat(food.isOverBudget()).isTrue();

        BudgetSummaryDto entertainment = find(summary, "Entertainment");
        assertThat(entertainment.getBudgetAmount()).isEqualByComparingTo("0.00");
        assertThat(entertainment.getActualAmount()).isEqualByComparingTo("80.00");

        BudgetSummaryDto transport = find(summary, "Transport");
        assertThat(transport.getBudgetAmount()).isEqualByComparingTo("100.00");
        assertThat(transport.getActualAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void getBudgetSummary_shouldUseLatestPriorBudgetForFutureMonths() {
        budgetRepository.saveAll(List.of(
            budget("testuser", "Food", "10000.00", 2026, 6),
            budget("testuser", "Food", "15000.00", 2026, 8),
            budget("otheruser", "Food", "99999.00", 2026, 7)
        ));

        BudgetSummaryDto julyFood = find(budgetService.getBudgetSummary("testuser", 2026, 7), "Food");
        BudgetSummaryDto septemberFood = find(budgetService.getBudgetSummary("testuser", 2026, 9), "Food");

        assertThat(julyFood.getBudgetAmount()).isEqualByComparingTo("10000.00");
        assertThat(septemberFood.getBudgetAmount()).isEqualByComparingTo("15000.00");
    }

    @Test
    void saveBudget_shouldCreateSelectedMonthOverrideWithoutChangingPriorBudget() {
        Budget june = budgetService.saveBudget("testuser", budget("Food", "10000.00", 2026, 6));

        Budget july = budgetService.saveBudget("testuser", budget("Food", "12000.00", 2026, 7));

        assertThat(july.getId()).isNotEqualTo(june.getId());
        assertThat(budgetService.getBudgetSummary("testuser", 2026, 6).get(0).getBudgetAmount())
            .isEqualByComparingTo("10000.00");
        assertThat(budgetService.getBudgetSummary("testuser", 2026, 8).get(0).getBudgetAmount())
            .isEqualByComparingTo("12000.00");
    }

    private BudgetSummaryDto find(List<BudgetSummaryDto> summary, String category) {
        return summary.stream()
            .filter(item -> category.equals(item.getCategory()))
            .findFirst()
            .orElseThrow();
    }

    private Budget budget(String category, String amount, int year, int month) {
        return Budget.builder()
            .category(category)
            .amount(new BigDecimal(amount))
            .budgetYear(year)
            .budgetMonth(month)
            .build();
    }

    private Budget budget(String userId, String category, String amount, int year, int month) {
        Budget budget = budget(category, amount, year, month);
        budget.setUserid(userId);
        return budget;
    }

    private Expense expense(String userId, String description, String category, String amount, LocalDate date) {
        Expense expense = new Expense();
        expense.setUserid(userId);
        expense.setDescription(description);
        expense.setCategory(category);
        expense.setAmount(new BigDecimal(amount));
        expense.setDate(date);
        return expense;
    }
}

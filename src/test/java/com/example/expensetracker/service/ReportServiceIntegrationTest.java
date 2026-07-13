package com.example.expensetracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.expensetracker.dto.CategoryBreakdownDto;
import com.example.expensetracker.dto.MonthlySummaryDto;
import com.example.expensetracker.dto.SpendingPeriodDto;
import com.example.expensetracker.dto.SpendingTrendDto;
import com.example.expensetracker.model.Expense;
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
class ReportServiceIntegrationTest {

    @Autowired
    private ReportService reportService;

    @Autowired
    private ExpenseRepository expenseRepository;

    @BeforeEach
    void setUp() {
        expenseRepository.deleteAll();
        expenseRepository.saveAll(List.of(
            expense("testuser", "Lunch", "Food", "100.00", LocalDate.of(2026, 6, 5)),
            expense("testuser", "Dinner", "Food", "50.00", LocalDate.of(2026, 6, 10)),
            expense("testuser", "Bus", "Transport", "50.00", LocalDate.of(2026, 6, 11)),
            expense("testuser", "May bill", "Electricity", "80.00", LocalDate.of(2026, 5, 1)),
            expense("otheruser", "Lunch", "Food", "999.00", LocalDate.of(2026, 6, 5))
        ));
    }

    @Test
    void monthlySummary_shouldAggregateSelectedUserAndMonth() {
        MonthlySummaryDto summary = reportService.getMonthlySummary("testuser", 2026, 6);

        assertThat(summary.getTotalAmount()).isEqualByComparingTo("200.00");
        assertThat(summary.getExpenseCount()).isEqualTo(3);
        assertThat(summary.getAverageAmount()).isEqualByComparingTo("66.67");
    }

    @Test
    void categoryBreakdown_shouldSortByAmountAndCalculatePercent() {
        List<CategoryBreakdownDto> breakdown = reportService.getCategoryBreakdown(
            "testuser",
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 30)
        );

        assertThat(breakdown).extracting(CategoryBreakdownDto::getCategory).containsExactly("Food", "Transport");
        assertThat(breakdown.get(0).getAmount()).isEqualByComparingTo("150.00");
        assertThat(breakdown.get(0).getPercentOfTotal()).isEqualByComparingTo("75.00");
        assertThat(breakdown.get(1).getPercentOfTotal()).isEqualByComparingTo("25.00");
    }

    @Test
    void spendingTrend_shouldFillEmptyMonths() {
        List<SpendingTrendDto> trend = reportService.getSpendingTrend("testuser", 2026, 6, 3, null);

        assertThat(trend).hasSize(3);
        assertThat(trend).extracting(SpendingTrendDto::getMonth).containsExactly(4, 5, 6);
        assertThat(trend.get(0).getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(trend.get(1).getTotalAmount()).isEqualByComparingTo("80.00");
        assertThat(trend.get(2).getTotalAmount()).isEqualByComparingTo("200.00");
    }

    @Test
    void spendingTrend_shouldFilterByCategoryWhenProvided() {
        List<SpendingTrendDto> trend = reportService.getSpendingTrend("testuser", 2026, 6, 3, "Food");

        assertThat(trend).hasSize(3);
        assertThat(trend).extracting(SpendingTrendDto::getMonth).containsExactly(4, 5, 6);
        assertThat(trend.get(0).getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(trend.get(1).getTotalAmount()).isEqualByComparingTo("0.00");
        assertThat(trend.get(2).getTotalAmount()).isEqualByComparingTo("150.00");
    }

    @Test
    void spendingByPeriod_shouldFillDailyBucketsAndFilterOwnerAndCategory() {
        List<SpendingPeriodDto> periods = reportService.getSpendingByPeriod(
            "testuser", LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 12),
            SpendingGranularity.DAY, "food");

        assertThat(periods).hasSize(4);
        assertThat(periods).extracting(SpendingPeriodDto::periodStart)
            .containsExactly(
                LocalDate.of(2026, 6, 9), LocalDate.of(2026, 6, 10),
                LocalDate.of(2026, 6, 11), LocalDate.of(2026, 6, 12));
        assertThat(periods).extracting(SpendingPeriodDto::totalAmount)
            .containsExactly(
                BigDecimal.ZERO, new BigDecimal("50.00"),
                BigDecimal.ZERO, BigDecimal.ZERO);
        assertThat(periods).extracting(SpendingPeriodDto::expenseCount)
            .containsExactly(0L, 1L, 0L, 0L);
    }

    @Test
    void spendingByPeriod_shouldUseMondayWeeksAndClipRangeEdges() {
        List<SpendingPeriodDto> periods = reportService.getSpendingByPeriod(
            "testuser", LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 16),
            SpendingGranularity.WEEK, null);

        assertThat(periods).extracting(
            SpendingPeriodDto::periodStart, SpendingPeriodDto::periodEnd)
            .containsExactly(
                tuple(LocalDate.of(2026, 6, 5), LocalDate.of(2026, 6, 7)),
                tuple(LocalDate.of(2026, 6, 8), LocalDate.of(2026, 6, 14)),
                tuple(LocalDate.of(2026, 6, 15), LocalDate.of(2026, 6, 16)));
        assertThat(periods).extracting(SpendingPeriodDto::totalAmount)
            .containsExactly(
                new BigDecimal("100.00"), new BigDecimal("100.00"), BigDecimal.ZERO);
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

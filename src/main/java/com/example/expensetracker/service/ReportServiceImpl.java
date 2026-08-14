package com.example.expensetracker.service;

import com.example.expensetracker.dto.CategoryBreakdownDto;
import com.example.expensetracker.dto.MonthlySummaryDto;
import com.example.expensetracker.dto.SpendingPeriodDto;
import com.example.expensetracker.dto.SpendingTrendDto;
import com.example.expensetracker.model.Expense;
import com.example.expensetracker.repository.ExpenseRepository;
import com.example.expensetracker.security.UserDataScope;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
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
public class ReportServiceImpl implements ReportService {

    private static final int MAX_TREND_MONTHS = 24;

    private final ExpenseRepository expenseRepository;

    @Override
    public MonthlySummaryDto getMonthlySummary(UserDataScope scope, int year, int month) {
        YearMonth yearMonth = validatedYearMonth(year, month);
        List<Expense> expenses = expensesForRange(scope, yearMonth.atDay(1), yearMonth.atEndOfMonth());
        BigDecimal total = total(expenses);
        long count = expenses.size();
        return MonthlySummaryDto.builder()
            .year(year)
            .month(month)
            .totalAmount(total)
            .expenseCount(count)
            .averageAmount(count == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP))
            .build();
    }

    @Override
    public List<CategoryBreakdownDto> getCategoryBreakdown(UserDataScope scope, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("fromDate and toDate are required");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must be on or before toDate");
        }

        List<Expense> expenses = expensesForRange(scope, fromDate, toDate);
        BigDecimal total = total(expenses);
        Map<String, BigDecimal> totalsByCategory = new LinkedHashMap<>();
        for (Expense expense : expenses) {
            String category = normalizeCategory(expense.getCategory());
            totalsByCategory.merge(category, nullToZero(expense.getAmount()), BigDecimal::add);
        }

        return totalsByCategory.entrySet().stream()
            .map(entry -> CategoryBreakdownDto.builder()
                .category(entry.getKey())
                .amount(entry.getValue())
                .percentOfTotal(percent(entry.getValue(), total))
                .build())
            .sorted(Comparator.comparing(CategoryBreakdownDto::getAmount).reversed())
            .toList();
    }

    @Override
    public List<SpendingTrendDto> getSpendingTrend(UserDataScope scope, int endYear, int endMonth, int months, String category) {
        YearMonth end = validatedYearMonth(endYear, endMonth);
        if (months < 1 || months > MAX_TREND_MONTHS) {
            throw new IllegalArgumentException("months must be between 1 and 24");
        }

        YearMonth start = end.minusMonths(months - 1L);
        List<Expense> expenses = expensesForRange(scope, start.atDay(1), end.atEndOfMonth());
        Map<YearMonth, BigDecimal> totalsByMonth = new LinkedHashMap<>();
        for (int index = 0; index < months; index += 1) {
            totalsByMonth.put(start.plusMonths(index), BigDecimal.ZERO);
        }
        for (Expense expense : expenses) {
            if (expense.getDate() == null) {
                continue;
            }
            if (hasText(category) && !normalizeCategory(expense.getCategory()).equalsIgnoreCase(category.trim())) {
                continue;
            }
            YearMonth expenseMonth = YearMonth.from(expense.getDate());
            if (totalsByMonth.containsKey(expenseMonth)) {
                totalsByMonth.merge(expenseMonth, nullToZero(expense.getAmount()), BigDecimal::add);
            }
        }

        return totalsByMonth.entrySet().stream()
            .map(entry -> SpendingTrendDto.builder()
                .year(entry.getKey().getYear())
                .month(entry.getKey().getMonthValue())
                .totalAmount(entry.getValue())
                .build())
            .toList();
    }

    @Override
    public List<SpendingPeriodDto> getSpendingByPeriod(
        UserDataScope scope,
        LocalDate fromDate,
        LocalDate toDate,
        SpendingGranularity granularity,
        String category
    ) {
        if (fromDate == null || toDate == null || granularity == null) {
            throw new IllegalArgumentException("Dates and granularity are required");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("fromDate must be on or before toDate");
        }

        List<Expense> expenses = expensesForRange(scope, fromDate, toDate).stream()
            .filter(expense -> !hasText(category)
                || normalizeCategory(expense.getCategory()).equalsIgnoreCase(category.trim()))
            .toList();
        List<SpendingPeriodDto> periods = new ArrayList<>();
        LocalDate periodStart = fromDate;
        while (!periodStart.isAfter(toDate)) {
            LocalDate periodEnd = granularity == SpendingGranularity.DAY
                ? periodStart
                : min(periodStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)), toDate);
            LocalDate start = periodStart;
            LocalDate end = periodEnd;
            List<Expense> matches = expenses.stream()
                .filter(expense -> expense.getDate() != null
                    && !expense.getDate().isBefore(start)
                    && !expense.getDate().isAfter(end))
                .toList();
            periods.add(new SpendingPeriodDto(start, end, total(matches), matches.size()));
            periodStart = periodEnd.plusDays(1);
        }
        return periods;
    }

    private List<Expense> expensesForRange(UserDataScope scope, LocalDate fromDate, LocalDate toDate) {
        return expenseRepository.findByUseridInAndDateBetween(scope.readableOwnerIds(), fromDate, toDate);
    }

    private YearMonth validatedYearMonth(int year, int month) {
        if (month < 1 || month > 12) {
            throw new IllegalArgumentException("month must be between 1 and 12");
        }
        return YearMonth.of(year, month);
    }

    private BigDecimal total(List<Expense> expenses) {
        return expenses.stream()
            .map(Expense::getAmount)
            .map(this::nullToZero)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LocalDate min(LocalDate left, LocalDate right) {
        return left.isBefore(right) ? left : right;
    }

    private BigDecimal percent(BigDecimal amount, BigDecimal total) {
        if (total.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return amount.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private BigDecimal nullToZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "Other";
        }
        return category.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

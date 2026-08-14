package com.example.expensetracker.service;

import com.example.expensetracker.dto.CategoryBreakdownDto;
import com.example.expensetracker.dto.MonthlySummaryDto;
import com.example.expensetracker.dto.SpendingPeriodDto;
import com.example.expensetracker.dto.SpendingTrendDto;
import com.example.expensetracker.security.UserDataScope;
import java.time.LocalDate;
import java.util.List;

public interface ReportService {
    MonthlySummaryDto getMonthlySummary(UserDataScope scope, int year, int month);
    List<CategoryBreakdownDto> getCategoryBreakdown(UserDataScope scope, LocalDate fromDate, LocalDate toDate);
    List<SpendingTrendDto> getSpendingTrend(UserDataScope scope, int endYear, int endMonth, int months, String category);
    List<SpendingPeriodDto> getSpendingByPeriod(
        UserDataScope scope,
        LocalDate fromDate,
        LocalDate toDate,
        SpendingGranularity granularity,
        String category
    );

    default MonthlySummaryDto getMonthlySummary(String userId, int year, int month) { return getMonthlySummary(UserDataScope.personal(userId), year, month); }
    default List<CategoryBreakdownDto> getCategoryBreakdown(String userId, LocalDate fromDate, LocalDate toDate) { return getCategoryBreakdown(UserDataScope.personal(userId), fromDate, toDate); }
    default List<SpendingTrendDto> getSpendingTrend(String userId, int endYear, int endMonth, int months, String category) { return getSpendingTrend(UserDataScope.personal(userId), endYear, endMonth, months, category); }
    default List<SpendingPeriodDto> getSpendingByPeriod(String userId, LocalDate fromDate, LocalDate toDate, SpendingGranularity granularity, String category) {
        return getSpendingByPeriod(UserDataScope.personal(userId), fromDate, toDate, granularity, category);
    }
}

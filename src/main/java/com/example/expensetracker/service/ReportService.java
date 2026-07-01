package com.example.expensetracker.service;

import com.example.expensetracker.dto.CategoryBreakdownDto;
import com.example.expensetracker.dto.MonthlySummaryDto;
import com.example.expensetracker.dto.SpendingTrendDto;
import java.time.LocalDate;
import java.util.List;

public interface ReportService {
    MonthlySummaryDto getMonthlySummary(String userId, int year, int month);
    List<CategoryBreakdownDto> getCategoryBreakdown(String userId, LocalDate fromDate, LocalDate toDate);
    List<SpendingTrendDto> getSpendingTrend(String userId, int endYear, int endMonth, int months, String category);
}

package com.example.expensetracker.controller;

import com.example.expensetracker.dto.CategoryBreakdownDto;
import com.example.expensetracker.dto.MonthlySummaryDto;
import com.example.expensetracker.dto.SpendingTrendDto;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.service.ReportService;
import com.example.expensetracker.service.RecurringExpenseService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Validated
public class ReportController {

    private final ReportService reportService;
    private final CurrentUserService currentUserService;
    private final RecurringExpenseService recurringExpenseService;

    @GetMapping("/monthly-summary")
    public ResponseEntity<MonthlySummaryDto> getMonthlySummary(
        @RequestParam int year,
        @RequestParam @Min(1) @Max(12) int month,
        Authentication authentication
    ) {
        String userId = currentUserService.getUserId(authentication);
        recurringExpenseService.generateDueExpenses(userId, LocalDate.now());
        return ResponseEntity.ok(reportService.getMonthlySummary(userId, year, month));
    }

    @GetMapping("/category-breakdown")
    public ResponseEntity<List<CategoryBreakdownDto>> getCategoryBreakdown(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        Authentication authentication
    ) {
        String userId = currentUserService.getUserId(authentication);
        recurringExpenseService.generateDueExpenses(userId, LocalDate.now());
        return ResponseEntity.ok(reportService.getCategoryBreakdown(userId, fromDate, toDate));
    }

    @GetMapping("/spending-trend")
    public ResponseEntity<List<SpendingTrendDto>> getSpendingTrend(
        @RequestParam int year,
        @RequestParam @Min(1) @Max(12) int month,
        @RequestParam(defaultValue = "6") @Min(1) @Max(24) int months,
        @RequestParam(required = false) String category,
        Authentication authentication
    ) {
        String userId = currentUserService.getUserId(authentication);
        recurringExpenseService.generateDueExpenses(userId, LocalDate.now());
        return ResponseEntity.ok(reportService.getSpendingTrend(userId, year, month, months, category));
    }
}

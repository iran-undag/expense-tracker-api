package com.example.expensetracker.controller;

import com.example.expensetracker.dto.BudgetMapper;
import com.example.expensetracker.dto.BudgetRequestDto;
import com.example.expensetracker.dto.BudgetResponseDto;
import com.example.expensetracker.dto.BudgetSummaryDto;
import com.example.expensetracker.model.Budget;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.service.BudgetService;
import com.example.expensetracker.service.RecurringExpenseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
@Validated
public class BudgetController {

    private final BudgetService budgetService;
    private final CurrentUserService currentUserService;
    private final RecurringExpenseService recurringExpenseService;

    @GetMapping
    public ResponseEntity<List<BudgetResponseDto>> getBudgets(
        @RequestParam int year,
        @RequestParam @Min(1) @Max(12) int month,
        Authentication authentication
    ) {
        String userId = currentUserService.getUserId(authentication);
        recurringExpenseService.generateDueExpenses(userId, LocalDate.now());
        return ResponseEntity.ok(
            budgetService.getBudgets(userId, year, month).stream()
                .map(BudgetMapper::toDto)
                .toList()
        );
    }

    @GetMapping("/summary")
    public ResponseEntity<List<BudgetSummaryDto>> getBudgetSummary(
        @RequestParam int year,
        @RequestParam @Min(1) @Max(12) int month,
        Authentication authentication
    ) {
        String userId = currentUserService.getUserId(authentication);
        recurringExpenseService.generateDueExpenses(userId, LocalDate.now());
        return ResponseEntity.ok(budgetService.getBudgetSummary(userId, year, month));
    }

    @PostMapping
    public ResponseEntity<BudgetResponseDto> createBudget(
        @Valid @RequestBody BudgetRequestDto request,
        Authentication authentication
    ) {
        String userId = currentUserService.getUserId(authentication);
        Budget saved = budgetService.saveBudget(userId, BudgetMapper.toEntity(request));
        return ResponseEntity.ok(BudgetMapper.toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<BudgetResponseDto> updateBudget(
        @PathVariable Long id,
        @Valid @RequestBody BudgetRequestDto request,
        Authentication authentication
    ) {
        try {
            String userId = currentUserService.getUserId(authentication);
            Budget updated = budgetService.updateBudget(id, userId, BudgetMapper.toEntity(request));
            return ResponseEntity.ok(BudgetMapper.toDto(updated));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBudget(
        @PathVariable Long id,
        Authentication authentication
    ) {
        try {
            String userId = currentUserService.getUserId(authentication);
            budgetService.deleteBudget(id, userId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

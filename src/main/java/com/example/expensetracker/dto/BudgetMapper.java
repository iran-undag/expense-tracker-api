package com.example.expensetracker.dto;

import com.example.expensetracker.model.Budget;

public final class BudgetMapper {

    private BudgetMapper() {}

    public static Budget toEntity(BudgetRequestDto request) {
        return Budget.builder()
            .category(request.getCategory())
            .budgetYear(request.getYear())
            .budgetMonth(request.getMonth())
            .amount(request.getAmount())
            .build();
    }

    public static BudgetResponseDto toDto(Budget budget) {
        return BudgetResponseDto.builder()
            .id(budget.getId())
            .category(budget.getCategory())
            .year(budget.getBudgetYear())
            .month(budget.getBudgetMonth())
            .amount(budget.getAmount())
            .userid(budget.getUserid())
            .build();
    }
}

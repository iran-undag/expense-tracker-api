package com.example.expensetracker.dto;

import com.example.expensetracker.model.RecurringExpense;

public final class RecurringExpenseMapper {

    private RecurringExpenseMapper() {}

    public static RecurringExpense toEntity(RecurringExpenseRequestDto request) {
        return RecurringExpense.builder()
            .description(request.getDescription())
            .amount(request.getAmount())
            .category(request.getCategory())
            .frequency(request.getFrequency())
            .startDate(request.getStartDate())
            .endDate(request.getEndDate())
            .active(request.getActive() == null || request.getActive())
            .build();
    }

    public static RecurringExpenseResponseDto toDto(RecurringExpense recurringExpense) {
        return RecurringExpenseResponseDto.builder()
            .id(recurringExpense.getId())
            .description(recurringExpense.getDescription())
            .amount(recurringExpense.getAmount())
            .category(recurringExpense.getCategory())
            .frequency(recurringExpense.getFrequency())
            .startDate(recurringExpense.getStartDate())
            .endDate(recurringExpense.getEndDate())
            .nextRunDate(recurringExpense.getNextRunDate())
            .active(recurringExpense.isActive())
            .userid(recurringExpense.getUserid())
            .build();
    }
}

package com.example.expensetracker.dto;

import com.example.expensetracker.model.Expense;
import java.util.List;
import java.util.stream.Collectors;

public class ExpenseMapper {

    public static Expense toEntity(ExpenseCreateRequestDto request) {
        if (request == null) {
            return null;
        }
        return Expense.builder()
                .description(request.getDescription())
                .amount(request.getAmount())
                .date(request.getDate())
                .category(request.getCategory())
                .userid(request.getUserid())
                .username(request.getUsername())
                .build();
    }

    public static ExpenseResponseDto toDto(Expense expense) {
        return ExpenseResponseDto.fromEntity(expense);
    }

    public static List<ExpenseResponseDto> toDtoList(List<Expense> expenses) {
        if (expenses == null) {
            return List.of();
        }
        return expenses.stream()
                .map(ExpenseResponseDto::fromEntity)
                .collect(Collectors.toList());
    }
}

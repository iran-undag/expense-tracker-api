package com.example.expensetracker.dto;

import com.example.expensetracker.model.ExpenseCategory;

public final class CategoryMapper {

    private CategoryMapper() {}

    public static ExpenseCategory toEntity(CategoryRequestDto request) {
        return ExpenseCategory.builder()
            .name(request.getName())
            .color(request.getColor())
            .icon(request.getIcon())
            .active(request.getActive() == null || request.getActive())
            .build();
    }

    public static CategoryResponseDto toDto(ExpenseCategory category) {
        return CategoryResponseDto.builder()
            .id(category.getId())
            .name(category.getName())
            .color(category.getColor())
            .icon(category.getIcon())
            .systemDefault(category.isSystemDefault())
            .active(category.isActive())
            .userid(category.getUserid())
            .build();
    }
}

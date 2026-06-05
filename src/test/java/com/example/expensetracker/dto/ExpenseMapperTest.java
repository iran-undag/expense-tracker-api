package com.example.expensetracker.dto;

import com.example.expensetracker.model.Expense;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ExpenseMapperTest {

    @Test
    void toEntity_shouldNotSetOwnerFieldsFromCreateRequest() {
        ExpenseCreateRequestDto request = ExpenseCreateRequestDto.builder()
                .description("Coffee")
                .amount(new BigDecimal("5.00"))
                .category("Food")
                .build();

        Expense expense = ExpenseMapper.toEntity(request);

        assertThat(expense.getDescription()).isEqualTo("Coffee");
        assertThat(expense.getAmount()).isEqualByComparingTo("5.00");
        assertThat(expense.getCategory()).isEqualTo("Food");
        assertThat(expense.getUserid()).isNull();
    }
}

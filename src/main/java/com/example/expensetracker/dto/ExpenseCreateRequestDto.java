package com.example.expensetracker.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request payload for creating a new expense")
public class ExpenseCreateRequestDto {

    @Schema(description = "Description of the expense", example = "Lunch at Subway")
    private String description;

    @NotNull(message = "Amount is required")
    @Schema(description = "Amount spent", example = "12.50")
    private BigDecimal amount;

    @Schema(description = "Date of the expense", example = "2024-05-08")
    private LocalDate date;

    @Schema(description = "Category of the expense", example = "Food")
    private String category;
}

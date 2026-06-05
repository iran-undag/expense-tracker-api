package com.example.expensetracker.dto;

import com.example.expensetracker.model.Expense;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Response payload for an expense record")
public class ExpenseResponseDto {

    @Schema(description = "Unique identifier", example = "1", accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(description = "Description of the expense", example = "Lunch at Subway")
    private String description;

    @Schema(description = "Amount spent", example = "12.50")
    private BigDecimal amount;

    @Schema(description = "Date of the expense", example = "2024-05-08")
    private LocalDate date;

    @Schema(description = "Category of the expense", example = "Food")
    private String category;

    @Schema(description = "User identifier associated with the expense", example = "user-123")
    private String userid;

    public static ExpenseResponseDto fromEntity(Expense expense) {
        if (expense == null) {
            return null;
        }
        return ExpenseResponseDto.builder()
                .id(expense.getId())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .date(expense.getDate())
                .category(expense.getCategory())
                .userid(expense.getUserid())
                .build();
    }
}

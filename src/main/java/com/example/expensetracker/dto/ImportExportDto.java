package com.example.expensetracker.dto;

import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ImportExportDto {
    @Builder.Default
    private Integer schemaVersion = 1;

    private LocalDateTime exportedAt;

    @Valid
    @Builder.Default
    private List<ExpenseCreateRequestDto> expenses = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<BudgetRequestDto> budgets = new ArrayList<>();

    @Valid
    @Builder.Default
    private List<CategoryRequestDto> categories = new ArrayList<>();
}

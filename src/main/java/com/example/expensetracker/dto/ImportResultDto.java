package com.example.expensetracker.dto;

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
public class ImportResultDto {
    private int importedExpenses;
    private int importedBudgets;
    private int importedCategories;

    @Builder.Default
    private List<ImportErrorDto> errors = new ArrayList<>();
}

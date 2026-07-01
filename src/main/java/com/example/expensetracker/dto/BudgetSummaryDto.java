package com.example.expensetracker.dto;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BudgetSummaryDto {
    private String category;
    private Integer year;
    private Integer month;
    private BigDecimal budgetAmount;
    private BigDecimal actualAmount;
    private BigDecimal remainingAmount;
    private BigDecimal percentUsed;
    private boolean overBudget;
}

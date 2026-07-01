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
public class MonthlySummaryDto {
    private Integer year;
    private Integer month;
    private BigDecimal totalAmount;
    private Long expenseCount;
    private BigDecimal averageAmount;
}

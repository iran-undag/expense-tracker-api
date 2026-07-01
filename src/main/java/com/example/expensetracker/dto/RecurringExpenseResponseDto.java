package com.example.expensetracker.dto;

import com.example.expensetracker.model.RecurringFrequency;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecurringExpenseResponseDto {
    private Long id;
    private String description;
    private BigDecimal amount;
    private String category;
    private RecurringFrequency frequency;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDate nextRunDate;
    private boolean active;
    private String userid;
}

package com.example.expensetracker.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Represents an individual expense record")
public class Expense {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
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
}

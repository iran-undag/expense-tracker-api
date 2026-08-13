package com.example.expensetracker.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Entity;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
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

    @Column(name = "demo_session_id")
    private UUID demoSessionId;

    @Column(name = "is_demo_seed", nullable = false)
    private boolean demoSeed;

    @Transient
    private String receiptType;

    @Transient
    private List<String> receiptItemDescriptions;
}

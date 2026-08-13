package com.example.expensetracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(
    name = "expense_category",
    uniqueConstraints = @UniqueConstraint(
        name = "uk_expense_category_user_name",
        columnNames = { "userid", "name" }
    )
)
public class ExpenseCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userid;

    @Column(nullable = false)
    private String name;

    private String color;

    private String icon;

    @Column(nullable = false)
    private boolean systemDefault;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "demo_session_id")
    private UUID demoSessionId;

    @Column(name = "is_demo_seed", nullable = false)
    private boolean demoSeed;
}

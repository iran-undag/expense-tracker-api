package com.example.expensetracker.demo.seed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "demo_seed_state")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemoSeedState {

    @Id
    private Short id;

    @Column(name = "template_version", nullable = false)
    private int templateVersion;

    @Column(name = "anchor_month", nullable = false)
    private LocalDate anchorMonth;

    @Column(name = "refreshed_at", nullable = false)
    private OffsetDateTime refreshedAt;
}

package com.example.expensetracker.demo.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "demo_session")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DemoSession {

    @Id
    private UUID id;

    @Column(name = "shared_account_id", nullable = false, length = 64)
    private String sharedAccountId;

    @Column(name = "persistence_owner_id", nullable = false, length = 96, unique = true)
    private String persistenceOwnerId;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "used_actions", nullable = false)
    private int usedActions;

    @Column(name = "reserved_actions", nullable = false)
    private int reservedActions;

    @Column(name = "resume_token_digest", nullable = false, length = 64, unique = true)
    private String resumeTokenDigest;
}

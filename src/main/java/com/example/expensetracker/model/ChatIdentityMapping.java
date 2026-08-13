package com.example.expensetracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(
    name = "chat_identity_mapping",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_chat_identity_dl_user", columnNames = "direct_line_user_id"),
        @UniqueConstraint(name = "uk_chat_identity_conversation", columnNames = "conversation_id")
    }
)
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatIdentityMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "direct_line_user_id", nullable = false, length = 128)
    private String directLineUserId;

    @Column(name = "conversation_id", nullable = false, length = 255)
    private String conversationId;

    @Column(name = "userid", nullable = false, length = 255)
    private String userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "demo_session_id")
    private UUID demoSessionId;
}

package com.example.expensetracker.repository;

import com.example.expensetracker.model.ChatIdentityMapping;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatIdentityMappingRepository extends JpaRepository<ChatIdentityMapping, Long> {

    Optional<ChatIdentityMapping> findByDirectLineUserIdAndConversationIdAndExpiresAtAfter(
        String directLineUserId,
        String conversationId,
        Instant now
    );
}

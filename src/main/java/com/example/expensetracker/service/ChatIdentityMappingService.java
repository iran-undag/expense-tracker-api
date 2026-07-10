package com.example.expensetracker.service;

import com.example.expensetracker.model.ChatIdentityMapping;
import com.example.expensetracker.repository.ChatIdentityMappingRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ChatIdentityMappingService {

    private final ChatIdentityMappingRepository mappingRepository;

    @Transactional
    public void createMapping(
        String directLineUserId,
        String conversationId,
        String userId,
        Instant expiresAt
    ) {
        if (!StringUtils.hasText(directLineUserId) || !directLineUserId.startsWith("dl_")) {
            throw new IllegalArgumentException("Direct Line user ID must start with dl_.");
        }

        mappingRepository.save(ChatIdentityMapping.builder()
            .directLineUserId(directLineUserId)
            .conversationId(conversationId)
            .userId(userId)
            .expiresAt(expiresAt)
            .build());
    }

    @Transactional(readOnly = true)
    public Optional<String> resolveUserId(
        String directLineUserId,
        String conversationId,
        Instant now
    ) {
        return mappingRepository
            .findByDirectLineUserIdAndConversationIdAndExpiresAtAfter(
                directLineUserId,
                conversationId,
                now
            )
            .map(ChatIdentityMapping::getUserId);
    }
}

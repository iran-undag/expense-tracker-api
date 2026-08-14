package com.example.expensetracker.service;

import com.example.expensetracker.model.ChatIdentityMapping;
import com.example.expensetracker.repository.ChatIdentityMappingRepository;
import com.example.expensetracker.security.UserDataScope;
import java.time.Instant;
import java.util.List;
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
        createMapping(
            directLineUserId,
            conversationId,
            UserDataScope.personal(userId),
            expiresAt
        );
    }

    @Transactional
    public void createMapping(
        String directLineUserId,
        String conversationId,
        UserDataScope scope,
        Instant expiresAt
    ) {
        if (!StringUtils.hasText(directLineUserId) || !directLineUserId.startsWith("dl_")) {
            throw new IllegalArgumentException("Direct Line user ID must start with dl_.");
        }
        if (scope.demo() != directLineUserId.startsWith("dl_demo_")) {
            throw new IllegalArgumentException("Direct Line user ID prefix must match its data scope.");
        }

        mappingRepository.save(ChatIdentityMapping.builder()
            .directLineUserId(directLineUserId)
            .conversationId(conversationId)
            .userId(scope.ownerId())
            .expiresAt(expiresAt)
            .demoSessionId(scope.demoSessionId())
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

    @Transactional(readOnly = true)
    public Optional<UserDataScope> resolveDataScope(
        String directLineUserId,
        String conversationId,
        Instant now
    ) {
        return mappingRepository
            .findByDirectLineUserIdAndConversationIdAndExpiresAtAfter(
                directLineUserId, conversationId, now)
            .map(mapping -> mapping.getDemoSessionId() == null
                ? UserDataScope.personal(mapping.getUserId())
                : new UserDataScope(
                    mapping.getUserId(),
                    List.of("demo:seed", mapping.getUserId()),
                    mapping.getDemoSessionId(),
                    true));
    }
}

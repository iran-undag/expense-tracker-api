package com.example.expensetracker.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.expensetracker.repository.ChatIdentityMappingRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

@SpringBootTest
@ActiveProfiles("dev")
@TestExecutionListeners({
    DependencyInjectionTestExecutionListener.class,
    DirtiesContextTestExecutionListener.class
})
class ChatIdentityMappingServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-10T08:00:00Z");

    @Autowired
    private ChatIdentityMappingService mappingService;

    @Autowired
    private ChatIdentityMappingRepository mappingRepository;

    @BeforeEach
    void setUp() {
        mappingRepository.deleteAll();
    }

    @Test
    void resolveUserId_returnsMappedUserForMatchingUnexpiredConversation() {
        mappingService.createMapping(
            "dl_random-user-id",
            "direct-line-conversation-id",
            "expense-owner-id",
            NOW.plusSeconds(1800)
        );

        assertThat(mappingService.resolveUserId(
            "dl_random-user-id",
            "direct-line-conversation-id",
            NOW
        )).contains("expense-owner-id");
    }

    @Test
    void resolveUserId_rejectsMismatchedConversation() {
        mappingService.createMapping(
            "dl_random-user-id",
            "direct-line-conversation-id",
            "expense-owner-id",
            NOW.plusSeconds(1800)
        );

        assertThat(mappingService.resolveUserId(
            "dl_random-user-id",
            "attacker-conversation-id",
            NOW
        )).isEmpty();
    }

    @Test
    void resolveUserId_rejectsExpiredMapping() {
        mappingService.createMapping(
            "dl_random-user-id",
            "direct-line-conversation-id",
            "expense-owner-id",
            NOW
        );

        assertThat(mappingService.resolveUserId(
            "dl_random-user-id",
            "direct-line-conversation-id",
            NOW
        )).isEmpty();
    }

    @Test
    void createMapping_rejectsDirectLineUserIdWithoutRequiredPrefix() {
        assertThatThrownBy(() -> mappingService.createMapping(
            "attacker-controlled-id",
            "direct-line-conversation-id",
            "expense-owner-id",
            NOW.plusSeconds(1800)
        ))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Direct Line user ID must start with dl_.");
    }
}

package com.example.expensetracker.chattool;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expensetracker.dto.MonthlySummaryDto;
import com.example.expensetracker.model.RecurringExpense;
import com.example.expensetracker.model.RecurringFrequency;
import com.example.expensetracker.repository.ChatIdentityMappingRepository;
import com.example.expensetracker.repository.ExpenseRepository;
import com.example.expensetracker.repository.RecurringExpenseOccurrenceRepository;
import com.example.expensetracker.repository.RecurringExpenseRepository;
import com.example.expensetracker.service.ChatIdentityMappingService;
import com.example.expensetracker.service.RecurringExpenseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class ChatToolServiceIntegrationTest {
    private static final String USER_ID = "chat-tool-owner";
    private static final String DIRECT_LINE_USER = "dl_chat_tool_owner";
    private static final String CONVERSATION = "chat-tool-conversation";

    @Autowired private ChatToolService service;
    @Autowired private ChatIdentityMappingService mappingService;
    @Autowired private RecurringExpenseService recurringExpenseService;
    @Autowired private ChatIdentityMappingRepository mappingRepository;
    @Autowired private RecurringExpenseOccurrenceRepository occurrenceRepository;
    @Autowired private RecurringExpenseRepository recurringRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void clean() {
        occurrenceRepository.deleteAll();
        recurringRepository.deleteAll();
        expenseRepository.deleteAll();
        mappingRepository.deleteAll();
    }

    @Test
    void generatesDueExpenseBeforeReadingAndDoesNotDuplicateOnRepeat() {
        Instant now = Instant.now();
        mappingService.createMapping(DIRECT_LINE_USER, CONVERSATION, USER_ID, now.plusSeconds(600));
        recurringExpenseService.saveRecurringExpense(USER_ID, RecurringExpense.builder()
            .description("Monthly subscription")
            .amount(new BigDecimal("125.00"))
            .category("Subscriptions")
            .frequency(RecurringFrequency.MONTHLY)
            .startDate(LocalDate.now().withDayOfMonth(1))
            .active(true)
            .build());

        ChatToolResponse first = service.execute(monthlyRequest(), now);
        ChatToolResponse second = service.execute(monthlyRequest(), now.plusSeconds(1));

        assertThat(((MonthlySummaryDto) first.result()).getTotalAmount())
            .isEqualByComparingTo("125.00");
        assertThat(((MonthlySummaryDto) second.result()).getTotalAmount())
            .isEqualByComparingTo("125.00");
        assertThat(expenseRepository.findByUserid(USER_ID)).hasSize(1);
        assertThat(occurrenceRepository.findAll()).hasSize(1);
    }

    private ChatToolRequest monthlyRequest() {
        LocalDate today = LocalDate.now();
        return new ChatToolRequest(
            DIRECT_LINE_USER,
            CONVERSATION,
            ChatToolName.MONTHLY_SUMMARY,
            objectMapper.valueToTree(new MonthlySummaryArguments(today.getYear(), today.getMonthValue())));
    }
}

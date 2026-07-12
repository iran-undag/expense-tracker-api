package com.example.expensetracker.chattool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatToolRequestValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private ChatToolRequestValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ChatToolRequestValidator(objectMapper);
    }

    @Test
    void convertsValidMonthlySummaryArguments() {
        ChatToolRequest request = request(
            ChatToolName.MONTHLY_SUMMARY,
            new MonthlySummaryArguments(2026, 7));

        ValidatedChatToolRequest validated = validator.validate(request);

        assertThat(validated.arguments()).isEqualTo(new MonthlySummaryArguments(2026, 7));
    }

    @Test
    void rejectsUnknownArgumentProperties() {
        ChatToolRequest request = new ChatToolRequest(
            "dl_user", "conversation", ChatToolName.MONTHLY_SUMMARY,
            objectMapper.createObjectNode().put("year", 2026).put("month", 7).put("userId", "other"));

        assertThatThrownBy(() -> validator.validate(request))
            .isInstanceOf(ChatToolValidationException.class);
    }

    @Test
    void rejectsExpenseRangeLongerThan366Days() {
        ChatToolRequest request = request(
            ChatToolName.EXPENSE_LOOKUP,
            new ExpenseLookupArguments(
                LocalDate.parse("2025-01-01"), LocalDate.parse("2026-01-02"),
                null, null, null, null, 0, 20));

        assertThatThrownBy(() -> validator.validate(request))
            .isInstanceOf(ChatToolValidationException.class)
            .hasMessageContaining("366 days");
    }

    @Test
    void rejectsInvalidDirectLineIdentity() {
        ChatToolRequest request = new ChatToolRequest(
            "user", "conversation", ChatToolName.MONTHLY_SUMMARY,
            objectMapper.valueToTree(new MonthlySummaryArguments(2026, 7)));

        assertThatThrownBy(() -> validator.validate(request))
            .isInstanceOf(ChatToolValidationException.class)
            .hasMessageContaining("dl_");
    }

    private ChatToolRequest request(ChatToolName tool, ChatToolArguments arguments) {
        return new ChatToolRequest(
            "dl_user", "conversation", tool, objectMapper.valueToTree(arguments));
    }
}

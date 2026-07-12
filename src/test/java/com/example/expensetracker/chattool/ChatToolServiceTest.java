package com.example.expensetracker.chattool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expensetracker.dto.MonthlySummaryDto;
import com.example.expensetracker.service.BudgetService;
import com.example.expensetracker.service.ChatIdentityMappingService;
import com.example.expensetracker.service.ExpenseService;
import com.example.expensetracker.service.RecurringExpenseService;
import com.example.expensetracker.service.ReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChatToolServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");

    @Mock private ChatIdentityMappingService mappingService;
    @Mock private RecurringExpenseService recurringExpenseService;
    @Mock private ReportService reportService;
    @Mock private BudgetService budgetService;
    @Mock private ExpenseService expenseService;

    private ChatToolService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules();
        service = new ChatToolService(
            new ChatToolRequestValidator(objectMapper), mappingService,
            recurringExpenseService, reportService, budgetService, expenseService,
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void resolvesOwnerThenGeneratesRecurringExpensesOnceBeforeReading() {
        when(mappingService.resolveUserId("dl_user", "conversation", NOW))
            .thenReturn(Optional.of("owner"));
        MonthlySummaryDto summary = MonthlySummaryDto.builder()
            .year(2026).month(7).totalAmount(new BigDecimal("125.00"))
            .expenseCount(2L).averageAmount(new BigDecimal("62.50")).build();
        when(reportService.getMonthlySummary("owner", 2026, 7)).thenReturn(summary);

        ChatToolResponse response = service.execute(monthlyRequest(), NOW);

        assertThat(response.result()).isEqualTo(summary);
        verify(recurringExpenseService).generateDueExpenses("owner", LocalDate.of(2026, 7, 12));
        verify(reportService).getMonthlySummary("owner", 2026, 7);
    }

    @Test
    void rejectsUnknownMappingBeforeRecurringGeneration() {
        when(mappingService.resolveUserId("dl_user", "conversation", NOW))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute(monthlyRequest(), NOW))
            .isInstanceOf(ChatIdentityNotFoundException.class);

        verify(recurringExpenseService, never()).generateDueExpenses(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private ChatToolRequest monthlyRequest() {
        return new ChatToolRequest(
            "dl_user", "conversation", ChatToolName.MONTHLY_SUMMARY,
            objectMapper.valueToTree(new MonthlySummaryArguments(2026, 7)));
    }
}

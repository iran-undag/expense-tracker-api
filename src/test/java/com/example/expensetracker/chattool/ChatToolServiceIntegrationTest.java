package com.example.expensetracker.chattool;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.expensetracker.dto.MonthlySummaryDto;
import com.example.expensetracker.dto.SpendingPeriodDto;
import com.example.expensetracker.model.Expense;
import com.example.expensetracker.model.ExpenseCategory;
import com.example.expensetracker.model.RecurringExpense;
import com.example.expensetracker.model.RecurringFrequency;
import com.example.expensetracker.repository.ChatIdentityMappingRepository;
import com.example.expensetracker.repository.ExpenseCategoryRepository;
import com.example.expensetracker.repository.ExpenseRepository;
import com.example.expensetracker.repository.RecurringExpenseOccurrenceRepository;
import com.example.expensetracker.repository.RecurringExpenseRepository;
import com.example.expensetracker.service.ChatIdentityMappingService;
import com.example.expensetracker.service.RecurringExpenseService;
import com.example.expensetracker.service.SpendingGranularity;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("dev")
class ChatToolServiceIntegrationTest {
    private static final String USER_ID = "chat-tool-owner";
    private static final String SECOND_OWNER_ID = "chat-tool-second-owner";
    private static final String DIRECT_LINE_USER = "dl_chat_tool_owner";
    private static final String CONVERSATION = "chat-tool-conversation";

    @Autowired private ChatToolService service;
    @Autowired private ChatIdentityMappingService mappingService;
    @Autowired private RecurringExpenseService recurringExpenseService;
    @Autowired private ChatIdentityMappingRepository mappingRepository;
    @Autowired private RecurringExpenseOccurrenceRepository occurrenceRepository;
    @Autowired private RecurringExpenseRepository recurringRepository;
    @Autowired private ExpenseCategoryRepository categoryRepository;
    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private Clock clock;

    @BeforeEach
    void clean() {
        occurrenceRepository.deleteAll();
        recurringRepository.deleteAll();
        expenseRepository.deleteAll();
        categoryRepository.deleteAll();
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

    @Test
    void recurringStatusReturnsPostGenerationNextRunDate() {
        Instant now = Instant.now();
        mappingService.createMapping(DIRECT_LINE_USER, CONVERSATION, USER_ID, now.plusSeconds(600));
        recurringExpenseService.saveRecurringExpense(USER_ID, RecurringExpense.builder()
            .description("Monthly subscription")
            .amount(new BigDecimal("125.00"))
            .category("Subscriptions")
            .frequency(RecurringFrequency.MONTHLY)
            .startDate(LocalDate.now())
            .active(true)
            .build());

        ChatToolResponse response = service.execute(new ChatToolRequest(
            DIRECT_LINE_USER,
            CONVERSATION,
            ChatToolName.RECURRING_EXPENSE_STATUS,
            objectMapper.valueToTree(new RecurringExpenseStatusArguments(false))), now);

        ChatBoundedList<?> bounded = (ChatBoundedList<?>) response.result();
        ChatRecurringExpenseResult result = (ChatRecurringExpenseResult) bounded.content().get(0);
        assertThat(result.nextRunDate()).isAfter(LocalDate.now());
        assertThat(result.active()).isTrue();
        assertThat(expenseRepository.findByUserid(USER_ID)).hasSize(1);
    }

    @Test
    void newToolsReturnOnlyDataForMappedOwner() {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(clock);
        LocalDate fromDate = today.minusDays(1);
        LocalDate toDate = today.plusDays(1);
        mappingService.createMapping(DIRECT_LINE_USER, CONVERSATION, USER_ID, now.plusSeconds(600));

        expenseRepository.saveAll(List.of(
            expense(USER_ID, "Mapped owner expense", "Mapped Owner Category", "11.00", today),
            expense(SECOND_OWNER_ID, "Second owner expense", "Second Owner Category", "999.99", today)));
        categoryRepository.saveAll(List.of(
            category(USER_ID, "Mapped Owner Category"),
            category(SECOND_OWNER_ID, "Second Owner Category")));
        RecurringExpense mappedOwnerRule = recurringExpenseService.saveRecurringExpense(USER_ID,
            recurring("Mapped owner rule", "Mapped Owner Rule Category", "5.00", today));
        RecurringExpense secondOwnerRule = recurringExpenseService.saveRecurringExpense(SECOND_OWNER_ID,
            recurring("Second owner rule", "Second Owner Rule Category", "777.00", today));

        ChatExpensePage expensePage = (ChatExpensePage) service.execute(new ChatToolRequest(
            DIRECT_LINE_USER,
            CONVERSATION,
            ChatToolName.EXPENSE_LOOKUP,
            objectMapper.valueToTree(new ExpenseLookupArguments(
                fromDate, toDate, null, null, null, null,
                ExpenseSortBy.DATE, ExpenseSortDirection.ASC, 0, 20))), now).result();
        @SuppressWarnings("unchecked")
        List<SpendingPeriodDto> periods = (List<SpendingPeriodDto>) service.execute(new ChatToolRequest(
            DIRECT_LINE_USER,
            CONVERSATION,
            ChatToolName.SPENDING_BY_PERIOD,
            objectMapper.valueToTree(new SpendingByPeriodArguments(
                fromDate, toDate, SpendingGranularity.DAY, null))), now).result();
        ChatBoundedList<?> categories = (ChatBoundedList<?>) service.execute(new ChatToolRequest(
            DIRECT_LINE_USER,
            CONVERSATION,
            ChatToolName.CATEGORY_LIST,
            objectMapper.valueToTree(new CategoryListArguments(false))), now).result();
        ChatBoundedList<?> recurringRules = (ChatBoundedList<?>) service.execute(new ChatToolRequest(
            DIRECT_LINE_USER,
            CONVERSATION,
            ChatToolName.RECURRING_EXPENSE_STATUS,
            objectMapper.valueToTree(new RecurringExpenseStatusArguments(false))), now).result();

        assertThat(expensePage.content())
            .extracting(ChatExpenseResult::description)
            .contains("Mapped owner expense", "Mapped owner rule")
            .doesNotContain("Second owner expense", "Second owner rule");
        assertThat(expensePage.content())
            .extracting(ChatExpenseResult::category)
            .doesNotContain("Second Owner Category", "Second Owner Rule Category");
        assertThat(expensePage.content())
            .extracting(ChatExpenseResult::amount)
            .doesNotContain(new BigDecimal("999.99"), new BigDecimal("777.00"));

        assertThat(periods)
            .filteredOn(period -> period.periodStart().equals(today))
            .singleElement()
            .satisfies(period -> {
                assertThat(period.totalAmount()).isEqualByComparingTo("16.00");
                assertThat(period.expenseCount()).isEqualTo(2);
            });
        assertThat(categories.content())
            .map(ChatCategoryResult.class::cast)
            .extracting(ChatCategoryResult::name)
            .contains("Mapped Owner Category")
            .doesNotContain("Second Owner Category");
        assertThat(recurringRules.content())
            .map(ChatRecurringExpenseResult.class::cast)
            .extracting(ChatRecurringExpenseResult::description)
            .containsExactly("Mapped owner rule")
            .doesNotContain("Second owner rule");
        assertThat(recurringRules.content())
            .map(ChatRecurringExpenseResult.class::cast)
            .extracting(ChatRecurringExpenseResult::category)
            .doesNotContain("Second Owner Rule Category");
        assertThat(recurringRules.content())
            .map(ChatRecurringExpenseResult.class::cast)
            .extracting(ChatRecurringExpenseResult::amount)
            .doesNotContain(new BigDecimal("777.00"));

        assertThat(occurrenceRepository.findAll())
            .singleElement()
            .satisfies(occurrence -> {
                assertThat(occurrence.getUserid()).isEqualTo(USER_ID);
                assertThat(occurrence.getRecurringExpenseId()).isEqualTo(mappedOwnerRule.getId());
                assertThat(occurrence.getOccurrenceDate()).isEqualTo(today);
            });
        assertThat(expenseRepository.findByUserid(USER_ID))
            .extracting(Expense::getDescription)
            .containsExactlyInAnyOrder("Mapped owner expense", "Mapped owner rule");
        assertThat(expenseRepository.findByUserid(SECOND_OWNER_ID))
            .extracting(Expense::getDescription)
            .containsExactly("Second owner expense");
        assertThat(recurringRepository.findById(mappedOwnerRule.getId()).orElseThrow().getNextRunDate())
            .isEqualTo(today.plusMonths(1));
        RecurringExpense untouchedSecondOwnerRule = recurringRepository
            .findById(secondOwnerRule.getId()).orElseThrow();
        assertThat(untouchedSecondOwnerRule.getNextRunDate()).isEqualTo(today);
        assertThat(untouchedSecondOwnerRule.isActive()).isTrue();
    }

    private Expense expense(
        String userId, String description, String category, String amount, LocalDate date
    ) {
        return Expense.builder()
            .userid(userId)
            .description(description)
            .category(category)
            .amount(new BigDecimal(amount))
            .date(date)
            .build();
    }

    private ExpenseCategory category(String userId, String name) {
        return ExpenseCategory.builder()
            .userid(userId)
            .name(name)
            .systemDefault(false)
            .active(true)
            .build();
    }

    private RecurringExpense recurring(
        String description, String category, String amount, LocalDate startDate
    ) {
        return RecurringExpense.builder()
            .description(description)
            .amount(new BigDecimal(amount))
            .category(category)
            .frequency(RecurringFrequency.MONTHLY)
            .startDate(startDate)
            .active(true)
            .build();
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

package com.example.expensetracker.chattool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.expensetracker.dto.MonthlySummaryDto;
import com.example.expensetracker.model.ExpenseCategory;
import com.example.expensetracker.model.RecurringExpense;
import com.example.expensetracker.model.RecurringFrequency;
import com.example.expensetracker.service.BudgetService;
import com.example.expensetracker.service.CategoryService;
import com.example.expensetracker.service.ChatIdentityMappingService;
import com.example.expensetracker.service.ExpenseFilterCriteria;
import com.example.expensetracker.service.ExpenseService;
import com.example.expensetracker.service.RecurringExpenseService;
import com.example.expensetracker.service.ReportService;
import com.example.expensetracker.service.SpendingGranularity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class ChatToolServiceTest {
    private static final Instant NOW = Instant.parse("2026-07-12T00:00:00Z");

    @Mock private ChatIdentityMappingService mappingService;
    @Mock private RecurringExpenseService recurringExpenseService;
    @Mock private CategoryService categoryService;
    @Mock private ReportService reportService;
    @Mock private BudgetService budgetService;
    @Mock private ExpenseService expenseService;

    private ChatToolService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        service = new ChatToolService(
            new ChatToolRequestValidator(objectMapper), mappingService,
            recurringExpenseService, categoryService, reportService, budgetService, expenseService,
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

    @Test
    void returnsActiveRecurringStatusAfterGenerationWithoutOwnerFields() {
        when(mappingService.resolveUserId("dl_user", "conversation", NOW))
            .thenReturn(Optional.of("owner"));
        when(recurringExpenseService.getRecurringExpenses("owner")).thenReturn(List.of(
            RecurringExpense.builder()
                .id(99L).userid("owner").description("Rent")
                .amount(new BigDecimal("15000.00")).category("Rent")
                .frequency(RecurringFrequency.MONTHLY)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextRunDate(LocalDate.of(2026, 8, 1)).active(true).build(),
            RecurringExpense.builder()
                .id(100L).userid("owner").description("Old plan")
                .amount(new BigDecimal("100.00")).category("Other")
                .frequency(RecurringFrequency.MONTHLY)
                .startDate(LocalDate.of(2025, 1, 1))
                .nextRunDate(LocalDate.of(2026, 1, 1)).active(false).build()));

        ChatToolResponse response = service.execute(request(
            ChatToolName.RECURRING_EXPENSE_STATUS,
            new RecurringExpenseStatusArguments(false)), NOW);

        ChatBoundedList<?> result = (ChatBoundedList<?>) response.result();
        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0)).isEqualTo(new ChatRecurringExpenseResult(
            "Rent", new BigDecimal("15000.00"), "Rent", RecurringFrequency.MONTHLY,
            LocalDate.of(2026, 1, 1), null, LocalDate.of(2026, 8, 1), true));
        assertThat(result.totalCount()).isOne();
        assertThat(result.truncated()).isFalse();
        InOrder order = inOrder(mappingService, recurringExpenseService);
        order.verify(mappingService).resolveUserId("dl_user", "conversation", NOW);
        order.verify(recurringExpenseService, times(1)).generateDueExpenses(
            "owner", LocalDate.of(2026, 7, 12));
        order.verify(recurringExpenseService).getRecurringExpenses("owner");
    }

    @Test
    void returnsRequestedCategoriesAfterGeneration() {
        when(mappingService.resolveUserId("dl_user", "conversation", NOW))
            .thenReturn(Optional.of("owner"));
        when(categoryService.getCategories("owner", true)).thenReturn(List.of(
            ExpenseCategory.builder().id(1L).userid("owner").name("Food")
                .systemDefault(true).active(true).build()));

        ChatBoundedList<?> categories = (ChatBoundedList<?>) service.execute(request(
            ChatToolName.CATEGORY_LIST, new CategoryListArguments(true)), NOW).result();

        assertThat(categories.content()).hasSize(1);
        assertThat(categories.content().get(0)).isEqualTo(
            new ChatCategoryResult("Food", true, true));
        InOrder order = inOrder(mappingService, recurringExpenseService, categoryService);
        order.verify(mappingService).resolveUserId("dl_user", "conversation", NOW);
        order.verify(recurringExpenseService, times(1)).generateDueExpenses(
            "owner", LocalDate.of(2026, 7, 12));
        order.verify(categoryService).getCategories("owner", true);
    }

    @Test
    void delegatesPeriodAggregationWithNormalizedCategoryAfterGeneration() {
        when(mappingService.resolveUserId("dl_user", "conversation", NOW))
            .thenReturn(Optional.of("owner"));
        SpendingByPeriodArguments periods = new SpendingByPeriodArguments(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
            SpendingGranularity.WEEK, " Food ");

        service.execute(request(ChatToolName.SPENDING_BY_PERIOD, periods), NOW);

        InOrder order = inOrder(mappingService, recurringExpenseService, reportService);
        order.verify(mappingService).resolveUserId("dl_user", "conversation", NOW);
        order.verify(recurringExpenseService, times(1)).generateDueExpenses(
            "owner", LocalDate.of(2026, 7, 12));
        order.verify(reportService).getSpendingByPeriod(
            "owner", periods.fromDate(), periods.toDate(), periods.granularity(), "Food");
    }

    @Test
    void truncatesCategoryResultsAtOneHundredWhileRetainingTotalCount() {
        when(mappingService.resolveUserId("dl_user", "conversation", NOW))
            .thenReturn(Optional.of("owner"));
        List<ExpenseCategory> categories = IntStream.rangeClosed(1, 101)
            .mapToObj(index -> ExpenseCategory.builder()
                .name("Category " + index).active(true).build())
            .toList();
        when(categoryService.getCategories("owner", false)).thenReturn(categories);

        ChatBoundedList<?> result = (ChatBoundedList<?>) service.execute(request(
            ChatToolName.CATEGORY_LIST, new CategoryListArguments(false)), NOW).result();

        assertThat(result.content()).hasSize(100);
        assertThat(result.totalCount()).isEqualTo(101);
        assertThat(result.truncated()).isTrue();
    }

    @Test
    void filtersAndTruncatesOrderedRecurringResultsUsingPreTruncationCount() {
        when(mappingService.resolveUserId("dl_user", "conversation", NOW))
            .thenReturn(Optional.of("owner"));
        List<RecurringExpense> rules = IntStream.rangeClosed(1, 102)
            .mapToObj(index -> RecurringExpense.builder()
                .description(index <= 99 ? "Active " + index : "Inactive " + (index - 99))
                .amount(BigDecimal.valueOf(index))
                .category("Category")
                .frequency(RecurringFrequency.MONTHLY)
                .startDate(LocalDate.of(2026, 1, 1))
                .nextRunDate(LocalDate.of(2026, 7, 12).plusDays(index))
                .active(index <= 99)
                .build())
            .toList();
        when(recurringExpenseService.getRecurringExpenses("owner")).thenReturn(rules);

        ChatBoundedList<?> activeOnly = (ChatBoundedList<?>) service.execute(request(
            ChatToolName.RECURRING_EXPENSE_STATUS,
            new RecurringExpenseStatusArguments(false)), NOW).result();
        ChatBoundedList<?> includingInactive = (ChatBoundedList<?>) service.execute(request(
            ChatToolName.RECURRING_EXPENSE_STATUS,
            new RecurringExpenseStatusArguments(true)), NOW).result();

        assertThat(activeOnly.content()).hasSize(99);
        assertThat(activeOnly.content())
            .map(ChatRecurringExpenseResult.class::cast)
            .extracting(ChatRecurringExpenseResult::description)
            .containsExactlyElementsOf(IntStream.rangeClosed(1, 99)
                .mapToObj(index -> "Active " + index).toList());
        assertThat(activeOnly.totalCount()).isEqualTo(99);
        assertThat(activeOnly.truncated()).isFalse();

        assertThat(includingInactive.content()).hasSize(100);
        assertThat(includingInactive.content())
            .map(ChatRecurringExpenseResult.class::cast)
            .extracting(ChatRecurringExpenseResult::description)
            .startsWith("Active 1")
            .endsWith("Active 99", "Inactive 1")
            .doesNotContain("Inactive 2", "Inactive 3");
        assertThat(includingInactive.totalCount()).isEqualTo(102);
        assertThat(includingInactive.truncated()).isTrue();
    }

    @Test
    void sortsExpenseLookupByAmountWithStableTiesAndPropagatesAmountBounds() {
        when(mappingService.resolveUserId("dl_user", "conversation", NOW))
            .thenReturn(Optional.of("owner"));
        when(expenseService.getAllExpenses(eq("owner"), any(), any()))
            .thenReturn(Page.empty());
        ExpenseLookupArguments lookup = new ExpenseLookupArguments(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
            null, null, new BigDecimal("10.00"), new BigDecimal("20.00"),
            ExpenseSortBy.AMOUNT, ExpenseSortDirection.ASC, 0, 5);

        service.execute(request(ChatToolName.EXPENSE_LOOKUP, lookup), NOW);

        ArgumentCaptor<ExpenseFilterCriteria> filters =
            ArgumentCaptor.forClass(ExpenseFilterCriteria.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(expenseService).getAllExpenses(eq("owner"), filters.capture(), pageable.capture());
        assertThat(filters.getValue().minAmount()).isEqualByComparingTo("10.00");
        assertThat(filters.getValue().maxAmount()).isEqualByComparingTo("20.00");
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
        Sort sort = pageable.getValue().getSort();
        assertThat(sort).extracting(Sort.Order::getProperty)
            .containsExactly("amount", "date", "id");
        assertThat(sort.getOrderFor("amount")).isNotNull();
        assertThat(sort.getOrderFor("amount").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(sort.getOrderFor("date")).isNotNull();
        assertThat(sort.getOrderFor("date").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("id")).isNotNull();
        assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void defaultsExpenseLookupToDateDescendingWithStableIdTieBreaker() {
        when(mappingService.resolveUserId("dl_user", "conversation", NOW))
            .thenReturn(Optional.of("owner"));
        when(expenseService.getAllExpenses(eq("owner"), any(), any()))
            .thenReturn(Page.empty());
        ExpenseLookupArguments lookup = new ExpenseLookupArguments(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
            null, null, null, null, null, null, 2, 20);

        service.execute(request(ChatToolName.EXPENSE_LOOKUP, lookup), NOW);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(expenseService).getAllExpenses(eq("owner"), any(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(20);
        Sort sort = pageable.getValue().getSort();
        assertThat(sort).extracting(Sort.Order::getProperty)
            .containsExactly("date", "id");
        assertThat(sort.getOrderFor("date")).isNotNull();
        assertThat(sort.getOrderFor("date").getDirection()).isEqualTo(Sort.Direction.DESC);
        assertThat(sort.getOrderFor("id")).isNotNull();
        assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    private ChatToolRequest monthlyRequest() {
        return request(ChatToolName.MONTHLY_SUMMARY, new MonthlySummaryArguments(2026, 7));
    }

    private ChatToolRequest request(ChatToolName tool, ChatToolArguments arguments) {
        return new ChatToolRequest(
            "dl_user", "conversation", tool, objectMapper.valueToTree(arguments));
    }
}

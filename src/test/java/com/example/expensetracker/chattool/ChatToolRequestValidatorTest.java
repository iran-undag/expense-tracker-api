package com.example.expensetracker.chattool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.expensetracker.service.SpendingGranularity;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChatToolRequestValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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
                null, null, null, null, null, null, 0, 20));

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

    @Test
    void convertsNewReadToolArgumentsAndLookupSorting() {
        assertThat(validator.validate(request(
            ChatToolName.RECURRING_EXPENSE_STATUS,
            new RecurringExpenseStatusArguments(false))).arguments())
            .isEqualTo(new RecurringExpenseStatusArguments(false));
        assertThat(validator.validate(request(
            ChatToolName.CATEGORY_LIST,
            new CategoryListArguments(true))).arguments())
            .isEqualTo(new CategoryListArguments(true));
        assertThat(validator.validate(request(
            ChatToolName.SPENDING_BY_PERIOD,
            new SpendingByPeriodArguments(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                SpendingGranularity.WEEK, "Food"))).arguments())
            .isEqualTo(new SpendingByPeriodArguments(
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
                SpendingGranularity.WEEK, "Food"));

        ExpenseLookupArguments lookup = new ExpenseLookupArguments(
            LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31),
            null, null, new BigDecimal("500"), new BigDecimal("5000"),
            ExpenseSortBy.AMOUNT, ExpenseSortDirection.DESC, 0, 5);
        ExpenseLookupArguments converted = (ExpenseLookupArguments) validator.validate(
            request(ChatToolName.EXPENSE_LOOKUP, lookup)).arguments();
        assertThat(converted.fromDate()).isEqualTo(lookup.fromDate());
        assertThat(converted.toDate()).isEqualTo(lookup.toDate());
        assertThat(converted.category()).isEqualTo(lookup.category());
        assertThat(converted.query()).isEqualTo(lookup.query());
        assertThat(converted.minAmount()).isEqualByComparingTo(lookup.minAmount());
        assertThat(converted.maxAmount()).isEqualByComparingTo(lookup.maxAmount());
        assertThat(converted.sortBy()).isEqualTo(lookup.sortBy());
        assertThat(converted.sortDirection()).isEqualTo(lookup.sortDirection());
        assertThat(converted.page()).isEqualTo(lookup.page());
        assertThat(converted.size()).isEqualTo(lookup.size());
    }

    @Test
    void rejectsInvalidPeriodAndSortArguments() {
        assertThatThrownBy(() -> validator.validate(new ChatToolRequest(
            "dl_user", "conversation", ChatToolName.SPENDING_BY_PERIOD,
            objectMapper.createObjectNode()
                .put("fromDate", "2026-01-01")
                .put("toDate", "2027-01-02")
                .put("granularity", "DAY"))))
            .isInstanceOf(ChatToolValidationException.class)
            .hasMessageContaining("366 days");

        assertThatThrownBy(() -> validator.validate(new ChatToolRequest(
            "dl_user", "conversation", ChatToolName.EXPENSE_LOOKUP,
            objectMapper.createObjectNode()
                .put("fromDate", "2026-07-01")
                .put("toDate", "2026-07-31")
                .put("sortBy", "DESCRIPTION")
                .put("sortDirection", "DESC")
                .put("page", 0)
                .put("size", 5))))
            .isInstanceOf(ChatToolValidationException.class);
    }

    @Test
    void rejectsUnknownPropertiesForNewTools() {
        assertThatThrownBy(() -> validator.validate(new ChatToolRequest(
            "dl_user", "conversation", ChatToolName.CATEGORY_LIST,
            objectMapper.createObjectNode()
                .put("includeInactive", false)
                .put("userId", "other"))))
            .isInstanceOf(ChatToolValidationException.class);
    }

    @Test
    void rejectsNonBooleanIncludeInactive() {
        assertThatThrownBy(() -> validator.validate(new ChatToolRequest(
            "dl_user", "conversation", ChatToolName.RECURRING_EXPENSE_STATUS,
            objectMapper.createObjectNode().put("includeInactive", "false"))))
            .isInstanceOf(ChatToolValidationException.class)
            .hasMessageContaining("includeInactive");
    }

    @Test
    void rejectsMissingNullTextualAndFractionalRequiredIntegers() {
        assertInvalid(ChatToolName.EXPENSE_LOOKUP, """
            {"fromDate":"2026-07-01","toDate":"2026-07-31","size":5}
            """);
        assertInvalid(ChatToolName.EXPENSE_LOOKUP, """
            {"fromDate":"2026-07-01","toDate":"2026-07-31","page":null,"size":5}
            """);
        assertInvalid(ChatToolName.EXPENSE_LOOKUP, """
            {"fromDate":"2026-07-01","toDate":"2026-07-31","page":"0","size":5}
            """);
        assertInvalid(ChatToolName.EXPENSE_LOOKUP, """
            {"fromDate":"2026-07-01","toDate":"2026-07-31","page":0,"size":5.5}
            """);
        assertInvalid(ChatToolName.MONTHLY_SUMMARY, """
            {"year":2026.0,"month":7}
            """);
        assertInvalid(ChatToolName.SPENDING_TREND, """
            {"year":2026,"month":7}
            """);
    }

    @Test
    void rejectsWrongJsonTypesForDatesTextAndEnums() {
        assertInvalid(ChatToolName.CATEGORY_BREAKDOWN, """
            {"fromDate":20260701,"toDate":"2026-07-31"}
            """);
        assertInvalid(ChatToolName.SPENDING_TREND, """
            {"year":2026,"month":7,"months":3,"category":12}
            """);
        assertInvalid(ChatToolName.EXPENSE_LOOKUP, """
            {"fromDate":"2026-07-01","toDate":"2026-07-31","sortBy":1,"page":0,"size":5}
            """);
        assertInvalid(ChatToolName.SPENDING_BY_PERIOD, """
            {"fromDate":"2026-07-01","toDate":"2026-07-31","granularity":true}
            """);
    }

    @Test
    void rejectsTextualAmountsWithoutChangingNumericAmountRules() {
        assertInvalid(ChatToolName.EXPENSE_LOOKUP, """
            {"fromDate":"2026-07-01","toDate":"2026-07-31","minAmount":"10.00","page":0,"size":5}
            """);
        assertInvalid(ChatToolName.EXPENSE_LOOKUP, """
            {"fromDate":"2026-07-01","toDate":"2026-07-31","maxAmount":"20.00","page":0,"size":5}
            """);
        assertInvalid(ChatToolName.EXPENSE_LOOKUP, """
            {"fromDate":"2026-07-01","toDate":"2026-07-31","minAmount":-1,"page":0,"size":5}
            """);
        assertInvalid(ChatToolName.EXPENSE_LOOKUP, """
            {"fromDate":"2026-07-01","toDate":"2026-07-31","minAmount":20,"maxAmount":10,"page":0,"size":5}
            """);
    }

    @Test
    void rejectsEnumWhitespaceLowercaseOrdinalsAndInvalidNames() {
        for (String granularity : new String[] { " DAY", "day", "0", "MONTH" }) {
            assertInvalid(ChatToolName.SPENDING_BY_PERIOD, """
                {"fromDate":"2026-07-01","toDate":"2026-07-31","granularity":"%s"}
                """.formatted(granularity));
        }
        for (String sortBy : new String[] { " DATE", "date", "0", "CATEGORY" }) {
            assertInvalid(ChatToolName.EXPENSE_LOOKUP, """
                {"fromDate":"2026-07-01","toDate":"2026-07-31","sortBy":"%s","page":0,"size":5}
                """.formatted(sortBy));
        }
        for (String sortDirection : new String[] { " DESC", "desc", "0", "DOWN" }) {
            assertInvalid(ChatToolName.EXPENSE_LOOKUP, """
                {"fromDate":"2026-07-01","toDate":"2026-07-31","sortDirection":"%s","page":0,"size":5}
                """.formatted(sortDirection));
        }
    }

    @Test
    void rejectsWhitespaceMalformedAndNoncanonicalRequiredDates() {
        assertInvalid(ChatToolName.CATEGORY_BREAKDOWN, """
            {"fromDate":" 2026-07-01","toDate":"2026-07-31"}
            """);
        assertInvalid(ChatToolName.CATEGORY_BREAKDOWN, """
            {"fromDate":"2026-07-01","toDate":"2026-02-30"}
            """);
        assertInvalid(ChatToolName.EXPENSE_LOOKUP, """
            {"fromDate":"2026-7-01","toDate":"2026-07-31","page":0,"size":5}
            """);
        assertInvalid(ChatToolName.EXPENSE_LOOKUP, """
            {"fromDate":"2026-07-01","toDate":"2026-07-31 ","page":0,"size":5}
            """);
        assertInvalid(ChatToolName.SPENDING_BY_PERIOD, """
            {"fromDate":"not-a-date","toDate":"2026-07-31","granularity":"DAY"}
            """);
        assertInvalid(ChatToolName.SPENDING_BY_PERIOD, """
            {"fromDate":"2026-07-01","toDate":"2026-7-31","granularity":"DAY"}
            """);
    }

    private ChatToolRequest request(ChatToolName tool, ChatToolArguments arguments) {
        return new ChatToolRequest(
            "dl_user", "conversation", tool, objectMapper.valueToTree(arguments));
    }

    private void assertInvalid(ChatToolName tool, String argumentsJson) {
        assertThatThrownBy(() -> validator.validate(new ChatToolRequest(
            "dl_user", "conversation", tool, objectMapper.readTree(argumentsJson))))
            .isInstanceOf(ChatToolValidationException.class);
    }
}

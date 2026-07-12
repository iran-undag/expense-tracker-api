package com.example.expensetracker.chattool;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ChatToolRequestValidator {
    private static final int MIN_YEAR = 2000;
    private static final int MAX_YEAR = 2100;
    private final ObjectMapper strictMapper;

    public ChatToolRequestValidator(ObjectMapper objectMapper) {
        this.strictMapper = objectMapper.copy()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public ValidatedChatToolRequest validate(ChatToolRequest request) {
        if (request == null) throw invalid("Request is required");
        validateIdentity(request.directLineUserId(), request.conversationId());
        if (request.tool() == null) throw invalid("Tool is required");
        if (request.arguments() == null || !request.arguments().isObject()) {
            throw invalid("Arguments must be an object");
        }
        ChatToolArguments arguments = switch (request.tool()) {
            case MONTHLY_SUMMARY -> convert(request.arguments(), MonthlySummaryArguments.class);
            case CATEGORY_BREAKDOWN -> convert(request.arguments(), CategoryBreakdownArguments.class);
            case SPENDING_TREND -> convert(request.arguments(), SpendingTrendArguments.class);
            case BUDGET_STATUS -> convert(request.arguments(), BudgetStatusArguments.class);
            case EXPENSE_LOOKUP -> convert(request.arguments(), ExpenseLookupArguments.class);
        };
        validateArguments(arguments);
        return new ValidatedChatToolRequest(
            request.directLineUserId(), request.conversationId(), request.tool(), arguments);
    }

    private void validateIdentity(String userId, String conversationId) {
        if (!StringUtils.hasText(userId) || !userId.startsWith("dl_") || userId.length() > 128) {
            throw invalid("Direct Line user ID must start with dl_ and be at most 128 characters");
        }
        if (!StringUtils.hasText(conversationId) || conversationId.length() > 255) {
            throw invalid("Conversation ID is required and must be at most 255 characters");
        }
    }

    private void validateArguments(ChatToolArguments arguments) {
        if (arguments instanceof MonthlySummaryArguments value) {
            validateYearMonth(value.year(), value.month());
        } else if (arguments instanceof CategoryBreakdownArguments value) {
            validateDateRange(value.fromDate(), value.toDate());
        } else if (arguments instanceof SpendingTrendArguments value) {
            validateYearMonth(value.year(), value.month());
            if (value.months() < 1 || value.months() > 24) throw invalid("Months must be between 1 and 24");
            validateText(value.category(), "Category");
        } else if (arguments instanceof BudgetStatusArguments value) {
            validateYearMonth(value.year(), value.month());
        } else if (arguments instanceof ExpenseLookupArguments value) {
            validateDateRange(value.fromDate(), value.toDate());
            validateText(value.category(), "Category");
            validateText(value.query(), "Query");
            validateAmounts(value.minAmount(), value.maxAmount());
            if (value.page() < 0 || value.page() > 100) throw invalid("Page must be between 0 and 100");
            if (value.size() < 1 || value.size() > 20) throw invalid("Size must be between 1 and 20");
        }
    }

    private void validateYearMonth(int year, int month) {
        if (year < MIN_YEAR || year > MAX_YEAR) throw invalid("Year must be between 2000 and 2100");
        if (month < 1 || month > 12) throw invalid("Month must be between 1 and 12");
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) throw invalid("Both dates are required");
        long days = ChronoUnit.DAYS.between(from, to);
        if (days < 0) throw invalid("From date must not be after to date");
        if (days > 365) throw invalid("Date range must not exceed 366 days");
    }

    private void validateText(String value, String name) {
        if (value != null && value.trim().length() > 100) throw invalid(name + " must be at most 100 characters");
    }

    private void validateAmounts(BigDecimal min, BigDecimal max) {
        if (min != null && min.signum() < 0 || max != null && max.signum() < 0) {
            throw invalid("Amounts must be non-negative");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw invalid("Minimum amount must not exceed maximum amount");
        }
    }

    private <T extends ChatToolArguments> T convert(JsonNode node, Class<T> type) {
        try {
            return strictMapper.treeToValue(node, type);
        } catch (Exception ex) {
            throw new ChatToolValidationException("Arguments do not match tool schema", ex);
        }
    }

    private ChatToolValidationException invalid(String message) {
        return new ChatToolValidationException(message);
    }
}

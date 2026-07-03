package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptCategoryNormalizerTest {

    private final ReceiptCategoryNormalizer normalizer = new ReceiptCategoryNormalizer(null);
    private final List<String> categories = List.of(
        "Food",
        "Groceries",
        "Transport",
        "Electricity",
        "Water",
        "Internet",
        "Phone",
        "Healthcare",
        "Shopping",
        "Travel",
        "Entertainment",
        "Other"
    );

    @Test
    void normalize_withExactMatch_shouldReturnDbCategoryName() {
        Expense expense = Expense.builder()
            .category("food")
            .build();

        assertThat(normalizer.normalize(expense, categories)).isEqualTo("Food");
    }

    @Test
    void normalize_withRestaurantKeyword_shouldReturnFood() {
        Expense expense = Expense.builder()
            .category("Restaurant")
            .build();

        assertThat(normalizer.normalize(expense, categories)).isEqualTo("Food");
    }

    @Test
    void normalize_withGasReceiptType_shouldReturnTransport() {
        Expense expense = Expense.builder()
            .receiptType("Fuel&Energy.Gas")
            .build();

        assertThat(normalizer.normalize(expense, categories)).isEqualTo("Transport");
    }

    @Test
    void normalize_withFuelMerchantKeyword_shouldReturnTransport() {
        List.of("ABC Petroleum", "Petron", "Shell", "Chevron", "Caltex").forEach(merchantName -> {
            Expense expense = Expense.builder()
                .description(merchantName)
                .build();

            assertThat(normalizer.normalize(expense, categories)).isEqualTo("Transport");
        });
    }

    @Test
    void normalize_withPharmacyMerchant_shouldReturnHealthcare() {
        Expense expense = Expense.builder()
            .description("Corner Pharmacy")
            .build();

        assertThat(normalizer.normalize(expense, categories)).isEqualTo("Healthcare");
    }

    @Test
    void normalize_withItemKeyword_shouldReturnGroceries() {
        Expense expense = Expense.builder()
            .receiptItemDescriptions(List.of("Fresh market vegetables"))
            .build();

        assertThat(normalizer.normalize(expense, categories)).isEqualTo("Groceries");
    }

    @Test
    void normalize_withUnknownCategory_shouldReturnOther() {
        Expense expense = Expense.builder()
            .category("General")
            .build();

        assertThat(normalizer.normalize(expense, categories)).isEqualTo("Other");
    }

    @Test
    void normalize_withBlankCategory_shouldReturnOther() {
        Expense expense = Expense.builder()
            .category(" ")
            .build();

        assertThat(normalizer.normalize(expense, categories)).isEqualTo("Other");
    }

    @Test
    void normalize_whenKeywordTargetIsMissing_shouldReturnOther() {
        Expense expense = Expense.builder()
            .category("Restaurant")
            .build();

        assertThat(normalizer.normalize(expense, List.of("Groceries", "Other"))).isEqualTo("Other");
    }
}

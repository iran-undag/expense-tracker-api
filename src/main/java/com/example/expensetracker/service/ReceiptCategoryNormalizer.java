package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;
import com.example.expensetracker.model.ExpenseCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReceiptCategoryNormalizer {

    private static final String FALLBACK_CATEGORY = "Other";
    private static final Map<String, List<String>> CATEGORY_KEYWORDS = categoryKeywords();

    private final CategoryService categoryService;

    public List<String> getActiveCategoryNames(String userId) {
        return categoryService.getCategories(userId, false).stream()
            .map(ExpenseCategory::getName)
            .toList();
    }

    public String normalize(String userId, Expense expense) {
        return normalize(expense, getActiveCategoryNames(userId));
    }

    public String normalize(Expense expense, List<String> activeCategoryNames) {
        Map<String, String> categoriesByKey = categoriesByKey(activeCategoryNames);
        if (categoriesByKey.isEmpty()) {
            return FALLBACK_CATEGORY;
        }

        Optional<String> exactMatch = findExactNonFallbackMatch(expense.getCategory(), categoriesByKey);
        if (exactMatch.isPresent()) {
            return exactMatch.get();
        }

        for (String evidence : categoryEvidence(expense)) {
            Optional<String> keywordMatch = findKeywordMatch(evidence, categoriesByKey);
            if (keywordMatch.isPresent()) {
                return keywordMatch.get();
            }
        }

        return categoriesByKey.getOrDefault(key(FALLBACK_CATEGORY), activeCategoryNames.get(0));
    }

    private Map<String, String> categoriesByKey(List<String> categories) {
        Map<String, String> categoriesByKey = new LinkedHashMap<>();
        for (String category : categories) {
            if (category != null && !category.isBlank()) {
                categoriesByKey.putIfAbsent(key(category), category.trim());
            }
        }
        return categoriesByKey;
    }

    private Optional<String> findExactMatch(String category, Map<String, String> categoriesByKey) {
        if (category == null || category.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(categoriesByKey.get(key(category)));
    }

    private Optional<String> findExactNonFallbackMatch(String category, Map<String, String> categoriesByKey) {
        Optional<String> exactMatch = findExactMatch(category, categoriesByKey);
        return exactMatch.filter(match -> !key(FALLBACK_CATEGORY).equals(key(match)));
    }

    private Optional<String> findKeywordMatch(String value, Map<String, String> categoriesByKey) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        String normalized = key(value);
        for (Map.Entry<String, List<String>> entry : CATEGORY_KEYWORDS.entrySet()) {
            String category = categoriesByKey.get(key(entry.getKey()));
            if (category == null) {
                continue;
            }
            if (entry.getValue().stream().anyMatch(keyword -> normalized.contains(key(keyword)))) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    private List<String> categoryEvidence(Expense expense) {
        List<String> evidence = new ArrayList<>();
        evidence.add(expense.getCategory());
        evidence.add(expense.getReceiptType());
        evidence.add(expense.getDescription());
        if (expense.getReceiptItemDescriptions() != null) {
            evidence.addAll(expense.getReceiptItemDescriptions());
        }
        return evidence;
    }

    private String key(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static Map<String, List<String>> categoryKeywords() {
        Map<String, List<String>> keywords = new LinkedHashMap<>();
        keywords.put("Food", List.of("restaurant", "cafe", "coffee", "dining", "meal", "lunch", "dinner", "bakery", "fast food"));
        keywords.put("Groceries", List.of("grocery", "groceries", "supermarket", "market", "mart"));
        keywords.put("Transport", List.of("transport", "taxi", "fuel", "gas", "gasoline", "petroleum", "petron", "shell", "chevron", "caltex", "transit", "train", "bus", "parking", "toll"));
        keywords.put("Electricity", List.of("electricity", "electric", "power", "meralco"));
        keywords.put("Water", List.of("water", "maynilad", "manila water"));
        keywords.put("Internet", List.of("internet", "broadband", "fiber", "wifi", "isp"));
        keywords.put("Phone", List.of("phone", "mobile", "cellular", "telco", "load", "prepaid", "postpaid"));
        keywords.put("Healthcare", List.of("health", "healthcare", "pharmacy", "medicine", "medical", "clinic", "hospital"));
        keywords.put("Shopping", List.of("shopping", "retail", "shop", "clothing", "apparel"));
        keywords.put("Travel", List.of("travel", "hotel", "flight", "airline", "lodging"));
        keywords.put("Entertainment", List.of("entertainment", "movie", "cinema", "concert", "game", "show"));
        return keywords;
    }
}

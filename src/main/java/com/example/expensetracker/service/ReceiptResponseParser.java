package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
class ReceiptResponseParser {

    private final ObjectMapper objectMapper;

    ReceiptResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    Expense parse(String content) {
        String merchantName = "Unknown Merchant";
        BigDecimal amount = BigDecimal.ZERO;
        LocalDate date = LocalDate.now();
        String category = "Other";

        try {
            String jsonContent = cleanJsonContent(content);
            JsonNode root = objectMapper.readTree(jsonContent);

            merchantName = root.path("merchantName").asText("Unknown Merchant");

            if (root.has("amount")) {
                amount = parseBigDecimal(root.path("amount").asText("0"));
            }
            if (root.has("date")) {
                date = parseLocalDate(root.path("date").asText(""));
            }
            if (root.has("category")) {
                category = root.path("category").asText("Other");
            }

            return Expense.builder()
                    .description(merchantName)
                    .amount(amount)
                    .date(date)
                    .category(category)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse receipt response as JSON, trying key-value line-by-line fallback. Error: {}",
                    e.getMessage());
        }

        try {
            Map<String, String> kvMap = new HashMap<>();
            String[] lines = content.split("\\r?\\n");
            for (String line : lines) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                int colonIdx = line.indexOf(':');
                if (colonIdx > 0) {
                    String key = line.substring(0, colonIdx).trim().replaceAll("^\"|\"$", "");
                    String val = line.substring(colonIdx + 1).trim().replaceAll("^\"|\"$", "").replaceAll(",$", "");
                    kvMap.put(key.toLowerCase(), val);
                }
            }

            if (kvMap.containsKey("merchantname")) {
                merchantName = kvMap.get("merchantname");
            } else if (kvMap.containsKey("merchant")) {
                merchantName = kvMap.get("merchant");
            }

            if (kvMap.containsKey("amount")) {
                amount = parseBigDecimal(kvMap.get("amount"));
            } else if (kvMap.containsKey("total")) {
                amount = parseBigDecimal(kvMap.get("total"));
            }

            if (kvMap.containsKey("date")) {
                date = parseLocalDate(kvMap.get("date"));
            }

            if (kvMap.containsKey("category")) {
                category = kvMap.get("category");
            }
        } catch (Exception e) {
            log.error("Failed to parse receipt response line-by-line", e);
        }

        return Expense.builder()
                .description(merchantName)
                .amount(amount)
                .date(date)
                .category(category)
                .build();
    }

    private String cleanJsonContent(String content) {
        String jsonContent = content.trim();
        if (jsonContent.contains("```json")) {
            return jsonContent.substring(jsonContent.indexOf("```json") + 7, jsonContent.lastIndexOf("```")).trim();
        }
        if (jsonContent.contains("```")) {
            return jsonContent.substring(jsonContent.indexOf("```") + 3, jsonContent.lastIndexOf("```")).trim();
        }
        return jsonContent;
    }

    private BigDecimal parseBigDecimal(String val) {
        if (val == null || val.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            String cleanVal = val.replaceAll("[^\\d.-]", "");
            return new BigDecimal(cleanVal);
        } catch (Exception e) {
            log.warn("Failed to parse BigDecimal from: {}, using ZERO", val);
            return BigDecimal.ZERO;
        }
    }

    private LocalDate parseLocalDate(String val) {
        if (val == null || val.trim().isEmpty()) {
            return LocalDate.now();
        }
        val = val.trim();
        try {
            if (val.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(val);
            }
            if (val.matches("\\d{1,2}-\\d{1,2}")) {
                int currentYear = LocalDate.now().getYear();
                String[] parts = val.split("-");
                String month = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                String day = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
                return LocalDate.parse(currentYear + "-" + month + "-" + day);
            }
            if (val.matches("\\d{2}-\\d{2}-\\d{4}")) {
                String[] parts = val.split("-");
                return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
            }
            if (val.matches("\\d{4}/\\d{2}/\\d{2}")) {
                return LocalDate.parse(val.replace('/', '-'));
            }
            if (val.matches("\\d{1,2}/\\d{1,2}/\\d{4}")) {
                String[] parts = val.split("/");
                return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            }
            if (val.matches("\\d{1,2}/\\d{1,2}")) {
                int currentYear = LocalDate.now().getYear();
                String[] parts = val.split("/");
                String month = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                String day = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
                return LocalDate.parse(currentYear + "-" + month + "-" + day);
            }
        } catch (Exception e) {
            log.warn("Failed to parse LocalDate from: {}, using today's date", val);
        }
        return LocalDate.now();
    }
}

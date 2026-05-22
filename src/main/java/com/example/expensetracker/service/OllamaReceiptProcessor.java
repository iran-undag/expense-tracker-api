package com.example.expensetracker.service;

import com.example.expensetracker.exception.ReceiptProcessingException;
import com.example.expensetracker.model.Expense;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Media;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class OllamaReceiptProcessor implements ReceiptProcessor {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public OllamaReceiptProcessor(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    @Override
    public Expense processReceipt(MultipartFile image) {
        log.info("Connecting to Ollama AI processor for receipt: {}", image.getOriginalFilename());
        try {
            UserMessage userMessage = new UserMessage(
                    "Extract expense details from this receipt image. Return ONLY a JSON object with fields: merchantName (string), amount (number), date (string YYYY-MM-DD), category (string). Do not wrap the JSON in markdown. Do not use ```json.",
                    List.of(new Media(MimeTypeUtils.IMAGE_JPEG, image.getResource())));

            ChatResponse response = chatModel.call(new Prompt(userMessage));
            String content = response.getResult().getOutput().getContent();
            log.info("Ollama AI processing completed. Received content: {}", content);

            Expense extracted = parseRobustly(content);

            log.debug("Extracted data from Ollama: {}", extracted);
            return extracted;

        } catch (Exception e) {
            throw new ReceiptProcessingException("Failed to process receipt with Ollama", e);
        }
    }

    private Expense parseRobustly(String content) {
        String merchantName = "Unknown Merchant";
        BigDecimal amount = BigDecimal.ZERO;
        LocalDate date = LocalDate.now();
        String category = "General";

        // Try parsing as JSON first
        try {
            String jsonContent = content.trim();
            // Basic JSON cleaning in case LLM adds markdown blocks
            if (jsonContent.contains("```json")) {
                jsonContent = jsonContent.substring(jsonContent.indexOf("```json") + 7, jsonContent.lastIndexOf("```"));
            } else if (jsonContent.contains("```")) {
                jsonContent = jsonContent.substring(jsonContent.indexOf("```") + 3, jsonContent.lastIndexOf("```"));
            }
            jsonContent = jsonContent.trim();

            JsonNode root = objectMapper.readTree(jsonContent);

            merchantName = root.path("merchantName").asText("Unknown Merchant");

            if (root.has("amount")) {
                amount = parseBigDecimal(root.path("amount").asText("0"));
            }
            if (root.has("date")) {
                date = parseLocalDate(root.path("date").asText(""));
            }
            if (root.has("category")) {
                category = root.path("category").asText("General");
            }

            return Expense.builder()
                    .description(merchantName)
                    .amount(amount)
                    .date(date)
                    .category(category)
                    .build();
        } catch (Exception e) {
            log.warn("Failed to parse Ollama response as JSON, trying key-value line-by-line fallback. Error: {}",
                    e.getMessage());
        }

        // Fallback: Parse line-by-line key-value pairs
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
            log.error("Failed to parse Ollama response line-by-line", e);
        }

        return Expense.builder()
                .description(merchantName)
                .amount(amount)
                .date(date)
                .category(category)
                .build();
    }

    private BigDecimal parseBigDecimal(String val) {
        if (val == null || val.trim().isEmpty()) {
            return BigDecimal.ZERO;
        }
        try {
            // Strip any non-numeric characters except decimal point and minus sign
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
            // Standard YYYY-MM-DD
            if (val.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return LocalDate.parse(val);
            }
            // MM-DD or M-D format (e.g. "07-12") -> assume current year
            if (val.matches("\\d{1,2}-\\d{1,2}")) {
                int currentYear = LocalDate.now().getYear();
                String[] parts = val.split("-");
                String month = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                String day = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
                return LocalDate.parse(currentYear + "-" + month + "-" + day);
            }
            // DD-MM-YYYY format
            if (val.matches("\\d{2}-\\d{2}-\\d{4}")) {
                String[] parts = val.split("-");
                return LocalDate.of(Integer.parseInt(parts[2]), Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
            }
            // Slash formats like YYYY/MM/DD
            if (val.matches("\\d{4}/\\d{2}/\\d{2}")) {
                return LocalDate.parse(val.replace('/', '-'));
            }
            // MM/DD format
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

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
import java.util.List;

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
                    "Extract expense details from this receipt image. Return ONLY a JSON object with fields: merchantName (string), amount (number), date (string YYYY-MM-DD), category (string).",
                    List.of(new Media(MimeTypeUtils.IMAGE_JPEG, image.getResource()))
            );

            ChatResponse response = chatModel.call(new Prompt(userMessage));
            String content = response.getResult().getOutput().getContent();
            log.info("Ollama AI processing completed. Received content: {}", content);
            
            // Basic JSON cleaning in case LLM adds markdown blocks
            if (content.contains("```json")) {
                content = content.substring(content.indexOf("```json") + 7, content.lastIndexOf("```"));
            } else if (content.contains("```")) {
                content = content.substring(content.indexOf("```") + 3, content.lastIndexOf("```"));
            }

            JsonNode root = objectMapper.readTree(content);

            Expense extracted = Expense.builder()
                    .description(root.path("merchantName").asText("Unknown"))
                    .amount(new BigDecimal(root.path("amount").asText("0")))
                    .date(LocalDate.parse(root.path("date").asText(LocalDate.now().toString())))
                    .category(root.path("category").asText("General"))
                    .build();
            
            log.debug("Extracted data from Ollama: {}", extracted);
            return extracted;

        } catch (Exception e) {
            throw new ReceiptProcessingException("Failed to process receipt with Ollama", e);
        }
    }
}

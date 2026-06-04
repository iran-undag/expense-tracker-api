package com.example.expensetracker.service;

import com.example.expensetracker.exception.ReceiptProcessingException;
import com.example.expensetracker.model.Expense;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.Media;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
public class OllamaReceiptProcessor implements ReceiptProcessor {

    private final ChatModel chatModel;
    private final ReceiptResponseParser responseParser;

    public OllamaReceiptProcessor(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.responseParser = new ReceiptResponseParser(objectMapper);
    }

    @Override
    public Expense processReceipt(MultipartFile image) {
        log.info("Connecting to Ollama AI processor for receipt: {}", image.getOriginalFilename());
        try {
            String contentType = image.getContentType();
            org.springframework.util.MimeType mimeType = (contentType != null) ? org.springframework.util.MimeType.valueOf(contentType) : MimeTypeUtils.IMAGE_JPEG;

            UserMessage userMessage = new UserMessage(
                    "Extract expense details from this receipt image. Return ONLY a JSON object with fields: merchantName (string), amount (number), date (string YYYY-MM-DD), category (string). Do not wrap the JSON in markdown. Do not use ```json.",
                    List.of(new Media(mimeType, image.getResource())));

            ChatResponse response = chatModel.call(new Prompt(userMessage));
            String content = response.getResult().getOutput().getContent();
            log.info("Ollama AI processing completed. Received content: {}", content);

            Expense extracted = responseParser.parse(content);

            log.debug("Extracted data from Ollama: {}", extracted);
            return extracted;

        } catch (Exception e) {
            throw new ReceiptProcessingException("Failed to process receipt with Ollama", e);
        }
    }
}

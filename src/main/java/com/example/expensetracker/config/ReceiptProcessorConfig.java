package com.example.expensetracker.config;

import com.example.expensetracker.service.AzureDocumentReceiptProcessor;
import com.example.expensetracker.service.OllamaReceiptProcessor;
import com.example.expensetracker.service.OpenVinoReceiptProcessor;
import com.example.expensetracker.service.ReceiptProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class ReceiptProcessorConfig {

    @Bean
    @ConditionalOnProperty(name = "receipt.processor.provider", havingValue = "azure")
    public ReceiptProcessor azureReceiptProcessor(
            @Value("${azure.documentintelligence.endpoint}") String endpoint,
            @Value("${azure.documentintelligence.key}") String key) {
        return new AzureDocumentReceiptProcessor(endpoint, key);
    }

    @Bean
    @ConditionalOnProperty(name = "receipt.processor.provider", havingValue = "ollama", matchIfMissing = true)
    public ReceiptProcessor ollamaReceiptProcessor(ChatModel chatModel, ObjectMapper objectMapper) {
        return new OllamaReceiptProcessor(chatModel, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(name = "receipt.processor.provider", havingValue = "openvino")
    public ReceiptProcessor openVinoReceiptProcessor(
            ObjectMapper objectMapper,
            @Value("${openvino.vision.base-url}") String baseUrl,
            @Value("${openvino.vision.chat-path}") String chatPath) {
        return new OpenVinoReceiptProcessor(new RestTemplate(), objectMapper, baseUrl, chatPath);
    }
}

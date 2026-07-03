package com.example.expensetracker.service;

import com.example.expensetracker.exception.ReceiptProcessingException;
import com.example.expensetracker.model.Expense;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class OllamaReceiptProcessorTest {

    private ChatModel chatModel;
    private OllamaReceiptProcessor processor;
    private MockMultipartFile receiptImage;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        processor = new OllamaReceiptProcessor(chatModel, new ObjectMapper());
        receiptImage = new MockMultipartFile(
                "image",
                "test-receipt.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{1, 2, 3});
    }

    @Test
    void processReceipt_withOllamaResponse_shouldExtractExpenseAndLogElapsedSeconds(CapturedOutput output) {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation("""
                {"merchantName":"Store","amount":12.50,"date":"2026-06-24","category":"Other"}
                """))));

        Expense expense = processor.processReceipt(receiptImage);

        assertThat(expense.getDescription()).isEqualTo("Store");
        assertThat(expense.getAmount()).isEqualByComparingTo(new BigDecimal("12.50"));
        assertThat(expense.getDate()).isEqualTo(LocalDate.of(2026, 6, 24));
        assertThat(expense.getCategory()).isEqualTo("Other");
        assertThat(output).containsPattern(
                "provider=ollama, filename=test-receipt\\.jpg, outcome=success, elapsedSeconds=\\d+\\.\\d{3}");
    }

    @Test
    void processReceipt_withAllowedCategories_shouldIncludeCategoryListInPrompt() {
        when(chatModel.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation("""
                {"merchantName":"Store","amount":12.50,"date":"2026-06-24","category":"Other"}
                """))));

        processor.processReceipt(receiptImage, List.of("Food", "Other"));

        var promptCaptor = forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String prompt = promptCaptor.getValue().getInstructions().get(0).getContent();
        assertThat(prompt).contains("Category must be exactly one of: Food, Other");
        assertThat(prompt).contains("Do not invent categories");
    }

    @Test
    void processReceipt_withOllamaFailure_shouldLogElapsedSeconds(CapturedOutput output) {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("Ollama unavailable"));

        assertThatThrownBy(() -> processor.processReceipt(receiptImage))
                .isInstanceOf(ReceiptProcessingException.class)
                .hasMessageContaining("Ollama");
        assertThat(output).containsPattern(
                "provider=ollama, filename=test-receipt\\.jpg, outcome=failure, elapsedSeconds=\\d+\\.\\d{3}");
    }
}

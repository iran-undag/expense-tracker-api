package com.example.expensetracker.service;

import com.example.expensetracker.config.CorrelationId;
import com.example.expensetracker.exception.ReceiptProcessingException;
import com.example.expensetracker.model.Expense;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AzureFunctionReceiptProcessorTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private MockMultipartFile receiptImage;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.getInterceptors().add((request, body, execution) -> {
            String correlationId = MDC.get(CorrelationId.MDC_KEY);
            if (correlationId != null && !correlationId.isBlank()) {
                request.getHeaders().set(CorrelationId.HEADER_NAME, correlationId);
            }
            return execution.execute(request, body);
        });
        server = MockRestServiceServer.bindTo(restTemplate).build();
        receiptImage = new MockMultipartFile(
                "image",
                "test-receipt.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{1, 2, 3});
    }

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationId.MDC_KEY);
    }

    @Test
    void processReceipt_withFunctionResponse_shouldExtractExpense() {
        AzureFunctionReceiptProcessor processor = new AzureFunctionReceiptProcessor(
                restTemplate,
                "http://localhost:7071/api/process-receipt",
                "test-key");

        server.expect(once(), requestTo("http://localhost:7071/api/process-receipt"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(content().bytes(new byte[]{1, 2, 3}))
                .andExpect(header("X-File-Name", "test-receipt.jpg"))
                .andExpect(header("x-functions-key", "test-key"))
                .andRespond(withSuccess("""
                        {
                          "description": "Starbucks",
                          "amount": 15.50,
                          "date": "2026-05-21",
                          "category": "Food"
                        }
                        """, MediaType.APPLICATION_JSON));

        Expense expense = processor.processReceipt(receiptImage);

        assertThat(expense.getDescription()).isEqualTo("Starbucks");
        assertThat(expense.getAmount()).isEqualByComparingTo(new BigDecimal("15.50"));
        assertThat(expense.getDate()).isEqualTo(LocalDate.of(2026, 5, 21));
        assertThat(expense.getCategory()).isEqualTo("Food");
        server.verify();
    }

    @Test
    void processReceipt_withoutFunctionKey_shouldNotSendFunctionKeyHeader() {
        AzureFunctionReceiptProcessor processor = new AzureFunctionReceiptProcessor(
                restTemplate,
                "http://localhost:7071/api/process-receipt",
                "");

        server.expect(once(), requestTo("http://localhost:7071/api/process-receipt"))
                .andExpect(request -> assertThat(request.getHeaders()).doesNotContainKey("x-functions-key"))
                .andRespond(withSuccess("""
                        {"description":"Store","amount":1,"date":"2024-01-01","category":"Other"}
                        """, MediaType.APPLICATION_JSON));

        Expense expense = processor.processReceipt(receiptImage);

        assertThat(expense.getDescription()).isEqualTo("Store");
        server.verify();
    }

    @Test
    void processReceipt_withHttpFailure_shouldThrow() {
        AzureFunctionReceiptProcessor processor = new AzureFunctionReceiptProcessor(
                restTemplate,
                "http://localhost:7071/api/process-receipt",
                "test-key");

        server.expect(once(), requestTo("http://localhost:7071/api/process-receipt"))
                .andRespond(withServerError().body("boom".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> processor.processReceipt(receiptImage))
                .isInstanceOf(ReceiptProcessingException.class)
                .hasMessageContaining("Azure receipt processor function");
        server.verify();
    }

    @Test
    void processReceipt_withFunctionValidationError_shouldThrowFunctionMessage() {
        AzureFunctionReceiptProcessor processor = new AzureFunctionReceiptProcessor(
                restTemplate,
                "http://localhost:7071/api/process-receipt",
                "test-key");

        server.expect(once(), requestTo("http://localhost:7071/api/process-receipt"))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "error": "Receipt Processing Failed",
                                  "message": "No documents found in the receipt image."
                                }
                                """));

        assertThatThrownBy(() -> processor.processReceipt(receiptImage))
                .isInstanceOf(ReceiptProcessingException.class)
                .hasMessage("No documents found in the receipt image.");
        server.verify();
    }

    @Test
    void processReceipt_withInvalidFunctionResponse_shouldThrow() {
        AzureFunctionReceiptProcessor processor = new AzureFunctionReceiptProcessor(
                restTemplate,
                "http://localhost:7071/api/process-receipt",
                "test-key");

        server.expect(once(), requestTo("http://localhost:7071/api/process-receipt"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> processor.processReceipt(receiptImage))
                .isInstanceOf(ReceiptProcessingException.class)
                .hasMessageContaining("invalid response");
        server.verify();
    }

    @Test
    void processReceipt_withCorrelationIdInMdc_shouldPropagateHeader() {
        MDC.put(CorrelationId.MDC_KEY, "receipt-correlation-id");
        AzureFunctionReceiptProcessor processor = new AzureFunctionReceiptProcessor(
                restTemplate,
                "http://localhost:7071/api/process-receipt",
                "test-key");

        server.expect(once(), requestTo("http://localhost:7071/api/process-receipt"))
                .andExpect(header(CorrelationId.HEADER_NAME, "receipt-correlation-id"))
                .andRespond(withSuccess("""
                        {"description":"Store","amount":1,"date":"2024-01-01","category":"Other"}
                        """, MediaType.APPLICATION_JSON));

        Expense expense = processor.processReceipt(receiptImage);

        assertThat(expense.getDescription()).isEqualTo("Store");
        server.verify();
    }
}

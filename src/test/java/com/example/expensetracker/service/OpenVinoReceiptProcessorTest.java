package com.example.expensetracker.service;

import com.example.expensetracker.config.CorrelationId;
import com.example.expensetracker.exception.ReceiptProcessingException;
import com.example.expensetracker.model.Expense;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class OpenVinoReceiptProcessorTest {

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private OpenVinoReceiptProcessor processor;
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
        processor = new OpenVinoReceiptProcessor(
                restTemplate,
                new ObjectMapper(),
                "http://localhost:8001/",
                "api/vision/chat");
        receiptImage = new MockMultipartFile(
                "image",
                "resto-receipt2.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{1, 2, 3});
    }

    @AfterEach
    void tearDown() {
        MDC.remove(CorrelationId.MDC_KEY);
    }

    @Test
    void processReceipt_withOpenVinoResponse_shouldExtractExpense(CapturedOutput output) {
        String response = """
                {
                  "response": "```json\\n{\\n  \\"merchantName\\": \\"SOUTH SUPERMARKET\\",\\n  \\"amount\\": 2466.65,\\n  \\"date\\": \\"04/05/2023\\",\\n  \\"category\\": \\"GROCERY SHOPPING\\"\\n}\\n```",
                  "model": "/home/user/openvino-llm/phi35-vision",
                  "device": "GPU",
                  "duration_seconds": 3.64
                }
                """;

        server.expect(once(), requestTo("http://localhost:8001/api/vision/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(content().string(containsString("Extract expense details from this receipt image")))
                .andExpect(content().string(containsString("Category must be exactly one of: Food, Groceries, Other")))
                .andExpect(content().string(containsString("resto-receipt2.jpg")))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));

        Expense expense = processor.processReceipt(receiptImage, java.util.List.of("Food", "Groceries", "Other"));

        assertThat(expense.getDescription()).isEqualTo("SOUTH SUPERMARKET");
        assertThat(expense.getAmount()).isEqualByComparingTo(new BigDecimal("2466.65"));
        assertThat(expense.getDate()).isEqualTo(LocalDate.of(2023, 4, 5));
        assertThat(expense.getCategory()).isEqualTo("GROCERY SHOPPING");
        assertThat(output).containsPattern(
                "provider=openvino, filename=resto-receipt2\\.jpg, outcome=success, elapsedSeconds=\\d+\\.\\d{3}");
        server.verify();
    }

    @Test
    void processReceipt_withoutResponseField_shouldThrow() {
        server.expect(once(), requestTo("http://localhost:8001/api/vision/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"model\":\"phi35-vision\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> processor.processReceipt(receiptImage))
                .isInstanceOf(ReceiptProcessingException.class)
                .hasMessageContaining("OpenVINO");
        server.verify();
    }

    @Test
    void processReceipt_withInvalidJsonEnvelope_shouldThrow() {
        server.expect(once(), requestTo("http://localhost:8001/api/vision/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> processor.processReceipt(receiptImage))
                .isInstanceOf(ReceiptProcessingException.class)
                .hasMessageContaining("OpenVINO");
        server.verify();
    }

    @Test
    void processReceipt_withHttpFailure_shouldThrow(CapturedOutput output) {
        server.expect(once(), requestTo("http://localhost:8001/api/vision/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError().body("boom".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> processor.processReceipt(receiptImage))
                .isInstanceOf(ReceiptProcessingException.class)
                .hasMessageContaining("OpenVINO");
        assertThat(output).containsPattern(
                "provider=openvino, filename=resto-receipt2\\.jpg, outcome=failure, elapsedSeconds=\\d+\\.\\d{3}");
        server.verify();
    }

    @Test
    void processReceipt_withCorrelationIdInMdc_shouldPropagateHeader() {
        MDC.put(CorrelationId.MDC_KEY, "receipt-correlation-id");

        server.expect(once(), requestTo("http://localhost:8001/api/vision/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(CorrelationId.HEADER_NAME, "receipt-correlation-id"))
                .andRespond(withSuccess("{\"response\":\"{\\\"merchantName\\\":\\\"Store\\\",\\\"amount\\\":1,\\\"date\\\":\\\"2024-01-01\\\",\\\"category\\\":\\\"Other\\\"}\"}", MediaType.APPLICATION_JSON));

        Expense expense = processor.processReceipt(receiptImage);

        assertThat(expense.getDescription()).isEqualTo("Store");
        server.verify();
    }
}

package com.example.expensetracker.service;

import com.example.expensetracker.exception.ReceiptProcessingException;
import com.example.expensetracker.model.Expense;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;

@Slf4j
public class AzureFunctionReceiptProcessor implements ReceiptProcessor {

    private static final String FUNCTION_KEY_HEADER = "x-functions-key";
    private static final String FILE_NAME_HEADER = "X-File-Name";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final RestTemplate restTemplate;
    private final String functionUrl;
    private final String functionKey;

    public AzureFunctionReceiptProcessor(RestTemplate restTemplate, String functionUrl, String functionKey) {
        this.restTemplate = restTemplate;
        this.functionUrl = functionUrl;
        this.functionKey = functionKey;
    }

    @Override
    public Expense processReceipt(MultipartFile image) {
        log.info("Calling Azure receipt processor function for receipt: {}", image.getOriginalFilename());
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            if (StringUtils.hasText(image.getOriginalFilename())) {
                headers.set(FILE_NAME_HEADER, image.getOriginalFilename());
            }
            if (StringUtils.hasText(functionKey)) {
                headers.set(FUNCTION_KEY_HEADER, functionKey);
            }

            ResponseEntity<ReceiptProcessorResponse> response = restTemplate.postForEntity(
                    functionUrl,
                    new HttpEntity<>(image.getBytes(), headers),
                    ReceiptProcessorResponse.class);

            ReceiptProcessorResponse body = response.getBody();
            if (body == null) {
                throw new ReceiptProcessingException("Azure receipt processor returned an empty response");
            }
            if (!body.isValid()) {
                throw new ReceiptProcessingException("Azure receipt processor returned an invalid response");
            }

            return Expense.builder()
                    .description(body.getDescription())
                    .amount(body.getAmount())
                    .date(body.getDate())
                    .category(body.getCategory())
                    .build();
        } catch (IOException e) {
            throw new ReceiptProcessingException("Failed to read receipt image", e);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().is4xxClientError()) {
                throw new ReceiptProcessingException(extractErrorMessage(e));
            }
            throw new ReceiptProcessingException("Failed to process receipt with Azure receipt processor function", e);
        } catch (RestClientException e) {
            throw new ReceiptProcessingException("Failed to process receipt with Azure receipt processor function", e);
        }
    }

    private String extractErrorMessage(HttpStatusCodeException e) {
        String responseBody = e.getResponseBodyAsString();
        if (!StringUtils.hasText(responseBody)) {
            return "Failed to process receipt with Azure receipt processor function";
        }

        try {
            JsonNode message = OBJECT_MAPPER.readTree(responseBody).get("message");
            if (message != null && StringUtils.hasText(message.asText())) {
                return message.asText();
            }
        } catch (IOException ignored) {
            // Fall back to the response body when the function returns plain text.
        }

        return responseBody;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ReceiptProcessorResponse {
        private String description;
        private BigDecimal amount;
        private LocalDate date;
        private String category;

        boolean isValid() {
            return StringUtils.hasText(description)
                    && amount != null
                    && date != null
                    && StringUtils.hasText(category);
        }
    }
}

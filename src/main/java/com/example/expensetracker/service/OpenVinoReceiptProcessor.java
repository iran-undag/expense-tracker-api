package com.example.expensetracker.service;

import com.example.expensetracker.exception.ReceiptProcessingException;
import com.example.expensetracker.model.Expense;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.StringJoiner;

@Slf4j
public class OpenVinoReceiptProcessor implements ReceiptProcessor {

    private static final String RECEIPT_EXTRACTION_PROMPT = "Extract expense details from this receipt image. "
            + "Return ONLY a JSON object with fields: merchantName (string), amount (number), "
            + "date (string YYYY-MM-DD), category (string). Do not wrap the JSON in markdown.";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ReceiptResponseParser responseParser;
    private final String chatUrl;

    public OpenVinoReceiptProcessor(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            String baseUrl,
            String chatPath) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.responseParser = new ReceiptResponseParser(objectMapper);
        this.chatUrl = normalizeUrl(baseUrl, chatPath);
    }

    @Override
    public Expense processReceipt(MultipartFile image, List<String> allowedCategories) {
        log.info("Connecting to OpenVINO AI processor for receipt: {}", image.getOriginalFilename());
        try {
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("prompt", buildReceiptExtractionPrompt(allowedCategories));
            body.add("image", image.getResource());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);
            String rawResponse = callReceiptProcessor(request, image.getOriginalFilename());
            String content = extractResponseContent(rawResponse);
            log.info("OpenVINO AI processing completed. Received content: {}", content);

            Expense extracted = responseParser.parse(content);
            log.debug("Extracted data from OpenVINO: {}", extracted);
            return extracted;
        } catch (RestClientException e) {
            throw new ReceiptProcessingException("Failed to call OpenVINO vision API", e);
        } catch (Exception e) {
            throw new ReceiptProcessingException("Failed to process receipt with OpenVINO", e);
        }
    }

    private String callReceiptProcessor(
            HttpEntity<MultiValueMap<String, Object>> request,
            String filename) {
        long startNanos = System.nanoTime();
        try {
            String response = restTemplate.postForObject(chatUrl, request, String.class);
            logElapsedTime(filename, "success", startNanos);
            return response;
        } catch (RestClientException e) {
            logElapsedTime(filename, "failure", startNanos);
            throw e;
        }
    }

    private void logElapsedTime(String filename, String outcome, long startNanos) {
        double elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000.0;
        log.info("Receipt processor call completed: provider=openvino, filename={}, outcome={}, elapsedSeconds={}",
                filename, outcome, String.format(Locale.ROOT, "%.3f", elapsedSeconds));
    }

    private String extractResponseContent(String rawResponse) {
        if (rawResponse == null || rawResponse.isBlank()) {
            throw new ReceiptProcessingException("OpenVINO vision API returned an empty response");
        }

        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            String response = root.path("response").asText();
            if (response == null || response.isBlank()) {
                throw new ReceiptProcessingException("OpenVINO vision API response did not include a response field");
            }
            return response;
        } catch (ReceiptProcessingException e) {
            throw e;
        } catch (Exception e) {
            throw new ReceiptProcessingException("Failed to parse OpenVINO vision API response", e);
        }
    }

    private String normalizeUrl(String baseUrl, String chatPath) {
        String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedChatPath = chatPath.startsWith("/") ? chatPath : "/" + chatPath;
        return normalizedBaseUrl + normalizedChatPath;
    }

    private String buildReceiptExtractionPrompt(List<String> allowedCategories) {
        if (allowedCategories == null || allowedCategories.isEmpty()) {
            return RECEIPT_EXTRACTION_PROMPT;
        }

        StringJoiner categoryList = new StringJoiner(", ");
        allowedCategories.forEach(categoryList::add);
        return RECEIPT_EXTRACTION_PROMPT + " Category must be exactly one of: " + categoryList
                + ". Do not invent categories. If unsure, use Other.";
    }
}

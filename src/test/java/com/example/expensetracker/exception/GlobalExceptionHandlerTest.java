package com.example.expensetracker.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.net.SocketTimeoutException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @Test
    void handleReceiptProcessingException_withTimeoutCause_shouldReturnTimeoutMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler("1MB");
        var exception = new ReceiptProcessingException(
                "Failed to process receipt with AI provider",
                new SocketTimeoutException("Read timed out"));

        var response = handler.handleReceiptProcessingException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT);
        assertThat(response.getBody()).isInstanceOf(Map.class);

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("status")).isEqualTo(HttpStatus.GATEWAY_TIMEOUT.value());
        assertThat(body.get("message")).isEqualTo(GlobalExceptionHandler.AI_PROCESSING_TIMEOUT_MESSAGE);
    }

    @Test
    void handleReceiptProcessingException_withoutTimeoutCause_shouldReturnOriginalMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler("1MB");
        var exception = new ReceiptProcessingException("Receipt data could not be parsed");

        var response = handler.handleReceiptProcessingException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("status")).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(body.get("message")).isEqualTo("Receipt data could not be parsed");
    }

    @Test
    void handleMaxUploadSizeExceededException_withDefaultLimit_shouldReturnDefaultLimitMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler("1MB");

        var response = handler.handleMaxUploadSizeExceededException(new MaxUploadSizeExceededException(0));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isInstanceOf(Map.class);

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("message")).isEqualTo("Max file size exceeded :  1MB");
    }

    @Test
    void handleMaxUploadSizeExceededException_withConfiguredLimit_shouldReturnConfiguredLimitMessage() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler("8MB");

        var response = handler.handleMaxUploadSizeExceededException(new MaxUploadSizeExceededException(0));

        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("message")).isEqualTo("Max file size exceeded :  8MB");
    }
}

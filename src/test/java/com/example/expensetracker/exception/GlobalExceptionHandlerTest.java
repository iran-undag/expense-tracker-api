package com.example.expensetracker.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

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

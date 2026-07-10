package com.example.expensetracker.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DirectLineTokenResponseDtoTest {

    @Test
    void toStringDoesNotExposeDirectLineToken() {
        String sensitiveToken = "eyJhbGciOiJSUzI1NiIsImtpZCI6InNlbnNpdGl2ZS10b2tlbiJ9";
        DirectLineTokenResponseDto response = DirectLineTokenResponseDto.builder()
            .token(sensitiveToken)
            .conversationId("conversation-123")
            .expiresInSeconds(1800)
            .userId("dl_user-123")
            .build();

        assertThat(response.toString())
            .doesNotContain(sensitiveToken)
            .contains("conversationId=conversation-123");
    }
}

package com.example.expensetracker.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SpeechTokenResponseDto {
    private String token;
    private String region;
    private int expiresInSeconds;
}

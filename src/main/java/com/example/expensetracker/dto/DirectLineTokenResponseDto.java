package com.example.expensetracker.dto;

import lombok.Builder;
import lombok.Data;
import lombok.ToString;

@Data
@Builder
public class DirectLineTokenResponseDto {
    @ToString.Exclude
    private String token;
    private String conversationId;
    private int expiresInSeconds;
    private String userId;
}

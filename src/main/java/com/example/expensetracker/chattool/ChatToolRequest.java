package com.example.expensetracker.chattool;

import com.fasterxml.jackson.databind.JsonNode;

public record ChatToolRequest(
    String directLineUserId,
    String conversationId,
    ChatToolName tool,
    JsonNode arguments
) {
}

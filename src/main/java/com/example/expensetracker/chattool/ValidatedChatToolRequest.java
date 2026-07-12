package com.example.expensetracker.chattool;

public record ValidatedChatToolRequest(
    String directLineUserId,
    String conversationId,
    ChatToolName tool,
    ChatToolArguments arguments
) {
}

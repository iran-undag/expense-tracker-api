package com.example.expensetracker.exception;

import com.example.expensetracker.chattool.ChatIdentityNotFoundException;
import com.example.expensetracker.chattool.ChatToolValidationException;
import com.example.expensetracker.chattool.ChatToolRateLimitException;
import com.example.expensetracker.controller.InternalChatToolController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = InternalChatToolController.class)
public class ChatToolExceptionHandler {

    @ExceptionHandler(ChatToolValidationException.class)
    ResponseEntity<ChatToolError> invalid(ChatToolValidationException exception) {
        return ResponseEntity.badRequest()
            .body(new ChatToolError("INVALID_TOOL_REQUEST", exception.getMessage()));
    }

    @ExceptionHandler(ChatIdentityNotFoundException.class)
    ResponseEntity<ChatToolError> missingIdentity() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ChatToolError("CHAT_IDENTITY_NOT_FOUND", "No active chat identity mapping"));
    }

    @ExceptionHandler(ChatToolRateLimitException.class)
    ResponseEntity<ChatToolError> rateLimited() {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(new ChatToolError("CHAT_TOOL_RATE_LIMITED", "Chat tool rate limit exceeded"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ChatToolError> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ChatToolError("CHAT_TOOL_UNAVAILABLE", "Chat tool is unavailable"));
    }

    record ChatToolError(String code, String message) {
    }
}

package com.example.expensetracker.chattool;

public class ChatToolValidationException extends RuntimeException {
    public ChatToolValidationException(String message) {
        super(message);
    }

    public ChatToolValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.example.expensetracker.chattool;

public class ChatIdentityNotFoundException extends RuntimeException {
    public ChatIdentityNotFoundException() {
        super("No active chat identity mapping");
    }
}

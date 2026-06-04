package com.example.expensetracker.auth;

public class AuthValidationException extends RuntimeException {

    public AuthValidationException(String message) {
        super(message);
    }

    public AuthValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.example.expensetracker.auth;

public interface AuthClient {
    AuthenticatedUser validate(String authorizationHeader);
}

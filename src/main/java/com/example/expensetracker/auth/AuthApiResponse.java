package com.example.expensetracker.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AuthApiResponse {

    private String id;
    private String userid;
    private String username;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserid() {
        return userid;
    }

    @JsonProperty("userId")
    public void setUserId(String userId) {
        this.userid = userId;
    }

    public void setUserid(String userid) {
        this.userid = userid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public AuthenticatedUser toAuthenticatedUser() {
        String resolvedUserId = userid != null && !userid.isBlank() ? userid : id;
        if (resolvedUserId == null || resolvedUserId.isBlank() || username == null || username.isBlank()) {
            throw new AuthValidationException("Auth API response is missing userid/id or username");
        }
        return new AuthenticatedUser(resolvedUserId, username);
    }
}

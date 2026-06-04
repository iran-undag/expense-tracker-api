package com.example.expensetracker.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@RequiredArgsConstructor
@Slf4j
public class RestAuthClient implements AuthClient {

    private final RestClient authRestClient;

    @Override
    public AuthenticatedUser validate(String authorizationHeader) {
        try {
            AuthApiResponse response = authRestClient.get()
                    .uri("/api/auth/me")
                    .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                    .retrieve()
                    .body(AuthApiResponse.class);

            if (response == null) {
                throw new AuthValidationException("Auth API response is empty");
            }
            return response.toAuthenticatedUser();
        } catch (AuthValidationException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.debug("Auth API validation failed: {}", ex.getMessage());
            throw new AuthValidationException("Bearer token could not be validated", ex);
        }
    }
}

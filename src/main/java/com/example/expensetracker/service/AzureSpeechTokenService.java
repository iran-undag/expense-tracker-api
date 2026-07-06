package com.example.expensetracker.service;

import com.example.expensetracker.dto.SpeechTokenResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
@RequiredArgsConstructor
public class AzureSpeechTokenService implements SpeechTokenService {

    private static final int TOKEN_EXPIRES_IN_SECONDS = 540;

    private final RestTemplate restTemplate;

    @Value("${azure.speech.key:}")
    private String speechKey;

    @Value("${azure.speech.region:}")
    private String speechRegion;

    @Value("${azure.speech.token-url:}")
    private String speechTokenUrl;

    @Override
    public SpeechTokenResponseDto issueToken() {
        if (!StringUtils.hasText(speechKey) || !StringUtils.hasText(speechRegion)) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Azure Speech is not configured");
        }

        String tokenEndpoint = StringUtils.hasText(speechTokenUrl)
            ? speechTokenUrl
            : "https://" + speechRegion + ".api.cognitive.microsoft.com/sts/v1.0/issueToken";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Ocp-Apim-Subscription-Key", speechKey);

        String token;
        try {
            token = restTemplate.exchange(
                tokenEndpoint,
                HttpMethod.POST,
                new HttpEntity<>("", headers),
                String.class
            ).getBody();
        } catch (RestClientException e) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Azure Speech token request failed", e);
        }

        if (!StringUtils.hasText(token)) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "Azure Speech did not return a token");
        }

        return SpeechTokenResponseDto.builder()
            .token(token)
            .region(speechRegion)
            .expiresInSeconds(TOKEN_EXPIRES_IN_SECONDS)
            .build();
    }
}

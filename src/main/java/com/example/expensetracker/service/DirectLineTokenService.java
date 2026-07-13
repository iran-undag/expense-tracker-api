package com.example.expensetracker.service;

import com.example.expensetracker.dto.DirectLineTokenResponseDto;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Service
@RequiredArgsConstructor
@Slf4j
public class DirectLineTokenService {

    private final RestTemplate restTemplate;
    private final ChatIdentityMappingService mappingService;
    private final ObjectMapper objectMapper;

    @Value("${azure.bot.direct-line.secret:}")
    private String directLineSecret;

    @Value("${azure.bot.direct-line.token-url}")
    private String tokenUrl;

    @Value("${azure.bot.direct-line.trusted-origins}")
    private String trustedOrigins;

    public DirectLineTokenResponseDto issueToken(String expenseUserId, String firstName) {
        List<String> origins = parseTrustedOrigins();
        if (!StringUtils.hasText(directLineSecret)
            || !StringUtils.hasText(tokenUrl)
            || origins.isEmpty()) {
            throw new ResponseStatusException(
                SERVICE_UNAVAILABLE,
                "Azure Bot Direct Line is not configured"
            );
        }

        String directLineUserId = "dl_" + UUID.randomUUID().toString().replace("-", "");
        DirectLineGenerateTokenRequest request = new DirectLineGenerateTokenRequest(
            new DirectLineUser(directLineUserId, firstName),
            origins
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(directLineSecret);
        headers.setContentType(MediaType.APPLICATION_JSON);

        DirectLineGenerateTokenResponse generated;
        try {
            generated = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                new HttpEntity<>(request, headers),
                DirectLineGenerateTokenResponse.class
            ).getBody();
        } catch (HttpStatusCodeException ex) {
            String errorCode = extractErrorCode(ex.getResponseBodyAsString());
            String errorMessage = extractErrorMessage(ex.getResponseBodyAsString());
            log.warn(
                "Azure Bot Direct Line token request was rejected: status={}, code={}, message={}",
                ex.getStatusCode().value(),
                errorCode,
                errorMessage
            );
            throw new ResponseStatusException(
                SERVICE_UNAVAILABLE,
                "Azure Bot Direct Line token request failed. Direct Line error code: " + errorCode,
                ex
            );
        } catch (RestClientException ex) {
            throw new ResponseStatusException(
                SERVICE_UNAVAILABLE,
                "Azure Bot Direct Line token request failed",
                ex
            );
        }

        if (generated == null
            || !StringUtils.hasText(generated.token())
            || !StringUtils.hasText(generated.conversationId())
            || generated.expiresIn() <= 0) {
            throw new ResponseStatusException(
                SERVICE_UNAVAILABLE,
                "Azure Bot Direct Line returned an invalid token response"
            );
        }

        mappingService.createMapping(
            directLineUserId,
            generated.conversationId(),
            expenseUserId,
            Instant.now().plusSeconds(generated.expiresIn())
        );

        return DirectLineTokenResponseDto.builder()
            .token(generated.token())
            .conversationId(generated.conversationId())
            .expiresInSeconds(generated.expiresIn())
            .userId(directLineUserId)
            .build();
    }

    private List<String> parseTrustedOrigins() {
        if (!StringUtils.hasText(trustedOrigins)) {
            return List.of();
        }
        return Arrays.stream(trustedOrigins.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();
    }

    private String extractErrorCode(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String errorCode = root.path("code").asText();
            if (!StringUtils.hasText(errorCode)) {
                errorCode = root.path("error").path("code").asText();
            }
            if (StringUtils.hasText(errorCode) && errorCode.matches("[A-Za-z0-9_.-]{1,64}")) {
                return errorCode;
            }
        } catch (Exception ignored) {
            // Do not log an untrusted provider response body.
        }
        return "unknown";
    }

    private String extractErrorMessage(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            String message = root.path("message").asText();
            if (!StringUtils.hasText(message)) {
                message = root.path("error").path("message").asText();
            }
            if (StringUtils.hasText(message)) {
                String sanitized = message.replaceAll("[\\p{Cntrl}]", " ").trim();
                return sanitized.substring(0, Math.min(sanitized.length(), 256));
            }
        } catch (Exception ignored) {
            // Do not log an untrusted provider response body.
        }
        return "not provided";
    }

    private record DirectLineGenerateTokenRequest(
        DirectLineUser user,
        List<String> trustedOrigins
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record DirectLineUser(String id, String name) {
        @Override
        public String toString() {
            return "DirectLineUser[id=" + id + ", namePresent=" + StringUtils.hasText(name) + "]";
        }
    }

    private record DirectLineGenerateTokenResponse(
        String conversationId,
        String token,
        @JsonProperty("expires_in") int expiresIn
    ) {
    }
}

package com.example.expensetracker.config;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestClientCustomizer aiProviderRestClientCustomizer(
            @Value("${ai.provider.connect-timeout}") Duration connectTimeout,
            @Value("${ai.provider.read-timeout}") Duration readTimeout) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(connectTimeout)
                .withReadTimeout(readTimeout);
        return builder -> builder.requestFactory(ClientHttpRequestFactories.get(settings));
    }

    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            @Value("${ai.provider.connect-timeout}") Duration connectTimeout,
            @Value("${ai.provider.read-timeout}") Duration readTimeout) {
        return builder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .additionalInterceptors((request, body, execution) -> {
                    String correlationId = MDC.get(CorrelationId.MDC_KEY);
                    if (StringUtils.hasText(correlationId)) {
                        request.getHeaders().set(CorrelationId.HEADER_NAME, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}

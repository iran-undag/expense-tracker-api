package com.example.expensetracker.config;

import org.slf4j.MDC;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder.additionalInterceptors((request, body, execution) -> {
            String correlationId = MDC.get(CorrelationId.MDC_KEY);
            if (StringUtils.hasText(correlationId)) {
                request.getHeaders().set(CorrelationId.HEADER_NAME, correlationId);
            }
            return execution.execute(request, body);
        }).build();
    }
}

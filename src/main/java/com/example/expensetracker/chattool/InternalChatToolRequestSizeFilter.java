package com.example.expensetracker.chattool;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class InternalChatToolRequestSizeFilter extends OncePerRequestFilter {
    private final int maximumBytes;
    private final ObjectMapper objectMapper;

    public InternalChatToolRequestSizeFilter(
        @Value("${chatbot.tools.max-request-bytes:16384}") int maximumBytes,
        ObjectMapper objectMapper
    ) {
        this.maximumBytes = maximumBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/chat-tools/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        if (request.getContentLengthLong() > maximumBytes) {
            response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), Map.of(
                "code", "CHAT_TOOL_REQUEST_TOO_LARGE",
                "message", "Chat tool request exceeds the size limit"));
            return;
        }
        filterChain.doFilter(request, response);
    }
}

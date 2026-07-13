package com.example.expensetracker.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expensetracker.chattool.ChatBoundedList;
import com.example.expensetracker.chattool.ChatCategoryResult;
import com.example.expensetracker.chattool.ChatIdentityNotFoundException;
import com.example.expensetracker.chattool.ChatToolName;
import com.example.expensetracker.chattool.ChatToolResponse;
import com.example.expensetracker.chattool.ChatToolService;
import com.example.expensetracker.chattool.ChatToolRateLimiter;
import com.example.expensetracker.dto.MonthlySummaryDto;
import com.example.expensetracker.exception.ChatToolExceptionHandler;
import com.example.expensetracker.security.ChatbotServiceSecurityConfig;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(InternalChatToolController.class)
@Import({ChatbotServiceSecurityConfig.class, ChatToolExceptionHandler.class})
class InternalChatToolControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ChatToolService service;
    @MockBean private JwtDecoder jwtDecoder;
    @MockBean private Clock clock;
    @MockBean private ChatToolRateLimiter rateLimiter;

    @org.junit.jupiter.api.BeforeEach
    void allowRequests() {
        when(rateLimiter.tryAcquire()).thenReturn(true);
    }

    @Test
    void returnsBoundedToolResponseForAuthorizedService() throws Exception {
        MonthlySummaryDto summary = MonthlySummaryDto.builder()
            .year(2026).month(7).totalAmount(new BigDecimal("125.00"))
            .expenseCount(2L).averageAmount(new BigDecimal("62.50")).build();
        when(service.execute(any(), any()))
            .thenReturn(new ChatToolResponse(ChatToolName.MONTHLY_SUMMARY, summary));

        mockMvc.perform(post("/internal/chat-tools/execute")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CHATBOT_TOOL_EXECUTOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(monthlyRequest()))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
            .andExpect(jsonPath("$.tool").value("MONTHLY_SUMMARY"))
            .andExpect(jsonPath("$.result.year").value(2026));
    }

    @Test
    void serializesNewToolResultForAuthorizedService() throws Exception {
        when(service.execute(any(), any())).thenReturn(new ChatToolResponse(
            ChatToolName.CATEGORY_LIST,
            new ChatBoundedList<>(
                List.of(new ChatCategoryResult("Food", true, true)), 1, false)));

        mockMvc.perform(post("/internal/chat-tools/execute")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CHATBOT_TOOL_EXECUTOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"directLineUserId":"dl_user","conversationId":"conversation",
                     "tool":"CATEGORY_LIST","arguments":{"includeInactive":false}}
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tool").value("CATEGORY_LIST"))
            .andExpect(jsonPath("$.result.content[0].name").value("Food"))
            .andExpect(jsonPath("$.result.content[0].userId").doesNotExist())
            .andExpect(jsonPath("$.result.totalCount").value(1));
    }

    @Test
    void returnsStableNotFoundForMissingIdentity() throws Exception {
        when(service.execute(any(), any())).thenThrow(new ChatIdentityNotFoundException());

        mockMvc.perform(post("/internal/chat-tools/execute")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CHATBOT_TOOL_EXECUTOR")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(monthlyRequest()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CHAT_IDENTITY_NOT_FOUND"));
    }

    private String monthlyRequest() {
        return """
            {"directLineUserId":"dl_user","conversationId":"conversation",
             "tool":"MONTHLY_SUMMARY","arguments":{"year":2026,"month":7}}
            """;
    }
}

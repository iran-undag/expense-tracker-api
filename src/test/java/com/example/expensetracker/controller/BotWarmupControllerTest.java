package com.example.expensetracker.controller;

import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.security.JwtTokenProvider;
import com.example.expensetracker.service.BotWarmupService;
import com.example.expensetracker.service.BotWarmupStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BotWarmupController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("dev")
class BotWarmupControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private BotWarmupService warmupService;
    @MockBean private CurrentUserService currentUserService;
    @MockBean private JwtTokenProvider jwtTokenProvider;

    @Test
    void returnsNoStoreWarmupStatusForAuthenticatedUser() throws Exception {
        var authentication = new TestingAuthenticationToken("testuser", null);
        when(currentUserService.getUserId(authentication)).thenReturn("expense-owner-id");
        when(warmupService.warmup("expense-owner-id")).thenReturn(BotWarmupStatus.READY);

        mockMvc.perform(post("/api/bot/warmup").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("ready"));
    }
}

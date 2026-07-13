package com.example.expensetracker.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expensetracker.dto.DirectLineTokenResponseDto;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.security.JwtTokenProvider;
import com.example.expensetracker.service.DirectLineTokenService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BotTokenController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("dev")
class BotTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DirectLineTokenService directLineTokenService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void controller_declaresBearerAuthenticationForSwagger() {
        SecurityRequirement requirement = BotTokenController.class
            .getAnnotation(SecurityRequirement.class);

        assertThat(requirement).isNotNull();
        assertThat(requirement.name()).isEqualTo("Bearer Authentication");
    }

    @Test
    void issueToken_returnsNoStoreDirectLineTokenForAuthenticatedUser() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        when(currentUserService.getUserId(authentication)).thenReturn("expense-owner-id");
        when(currentUserService.getFirstName(authentication)).thenReturn("Juan");
        when(directLineTokenService.issueToken("expense-owner-id", "Juan"))
            .thenReturn(DirectLineTokenResponseDto.builder()
                .token("short-lived-token")
                .conversationId("conversation-123")
                .expiresInSeconds(1800)
                .userId("dl_random-user-id")
                .build());

        mockMvc.perform(post("/api/bot/direct-line/token").principal(authentication))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.token").value("short-lived-token"))
            .andExpect(jsonPath("$.conversationId").value("conversation-123"))
            .andExpect(jsonPath("$.expiresInSeconds").value(1800))
            .andExpect(jsonPath("$.userId").value("dl_random-user-id"));
        verify(directLineTokenService).issueToken("expense-owner-id", "Juan");
    }
}

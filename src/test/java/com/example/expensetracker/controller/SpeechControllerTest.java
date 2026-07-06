package com.example.expensetracker.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expensetracker.dto.SpeechTokenResponseDto;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.security.JwtTokenProvider;
import com.example.expensetracker.service.SpeechTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SpeechController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("dev")
class SpeechControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SpeechTokenService speechTokenService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void issueToken_shouldReturnAzureSpeechTokenForAuthenticatedUser() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        when(currentUserService.getUserId(authentication)).thenReturn("testuser");
        when(speechTokenService.issueToken()).thenReturn(SpeechTokenResponseDto.builder()
            .token("speech-token")
            .region("southeastasia")
            .expiresInSeconds(540)
            .build());

        mockMvc.perform(post("/api/speech/token").principal(authentication))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("speech-token"))
            .andExpect(jsonPath("$.region").value("southeastasia"))
            .andExpect(jsonPath("$.expiresInSeconds").value(540));
    }
}

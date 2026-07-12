package com.example.expensetracker.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(ChatbotServiceSecurityProbeController.class)
@Import(ChatbotServiceSecurityConfig.class)
class ChatbotServiceSecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void rejectsMissingBearerToken() throws Exception {
        mockMvc.perform(post("/internal/chat-tools/probe"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsOrdinaryAuthenticatedUserWithoutServiceRole() throws Exception {
        mockMvc.perform(post("/internal/chat-tools/probe")
                .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_expense.read"))))
            .andExpect(status().isForbidden());
    }

    @Test
    void acceptsChatbotToolExecutorRole() throws Exception {
        mockMvc.perform(post("/internal/chat-tools/probe")
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_CHATBOT_TOOL_EXECUTOR"))))
            .andExpect(status().isNoContent());
    }

}

@RestController
class ChatbotServiceSecurityProbeController {
    @PostMapping("/internal/chat-tools/probe")
    ResponseEntity<Void> probe() {
        return ResponseEntity.noContent().build();
    }
}

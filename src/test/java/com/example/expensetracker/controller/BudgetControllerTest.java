package com.example.expensetracker.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expensetracker.model.Budget;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.security.JwtTokenProvider;
import com.example.expensetracker.service.BudgetService;
import com.example.expensetracker.service.RecurringExpenseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BudgetController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("dev")
class BudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BudgetService budgetService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RecurringExpenseService recurringExpenseService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getBudgets_shouldReturnUserBudgetsForMonth() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        Budget budget = budget("testuser", "Food", "500.00", 2026, 6);
        budget.setId(1L);
        when(currentUserService.getUserId(authentication)).thenReturn("testuser");
        when(budgetService.getBudgets("testuser", 2026, 6)).thenReturn(List.of(budget));

        mockMvc.perform(get("/api/budgets")
                .param("year", "2026")
                .param("month", "6")
                .principal(authentication))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].category").value("Food"))
            .andExpect(jsonPath("$[0].amount").value(500.00))
            .andExpect(jsonPath("$[0].userid").value("testuser"));
    }

    @Test
    void createBudget_shouldSetAuthenticatedUser() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        Budget saved = budget("testuser", "Food", "500.00", 2026, 6);
        saved.setId(1L);
        when(currentUserService.getUserId(authentication)).thenReturn("testuser");
        when(budgetService.saveBudget(eq("testuser"), any(Budget.class))).thenReturn(saved);

        mockMvc.perform(post("/api/budgets")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BudgetPayload("Food", 2026, 6, new BigDecimal("500.00")))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.category").value("Food"))
            .andExpect(jsonPath("$.userid").value("testuser"));

        var budgetCaptor = forClass(Budget.class);
        verify(budgetService).saveBudget(eq("testuser"), budgetCaptor.capture());
        assertThat(budgetCaptor.getValue().getUserid()).isNull();
        assertThat(budgetCaptor.getValue().getCategory()).isEqualTo("Food");
    }

    @Test
    void updateBudget_shouldReturn404WhenMissing() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        when(currentUserService.getUserId(authentication)).thenReturn("testuser");
        when(budgetService.updateBudget(eq(1L), eq("testuser"), any(Budget.class)))
            .thenThrow(new RuntimeException("missing"));

        mockMvc.perform(put("/api/budgets/1")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new BudgetPayload("Food", 2026, 6, new BigDecimal("500.00")))))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteBudget_shouldReturnNoContent() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        when(currentUserService.getUserId(authentication)).thenReturn("testuser");

        mockMvc.perform(delete("/api/budgets/1").principal(authentication))
            .andExpect(status().isNoContent());

        verify(budgetService).deleteBudget(1L, "testuser");
    }

    @Test
    void createBudget_shouldReturn400WhenAmountMissing() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);

        mockMvc.perform(post("/api/budgets")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"Food\",\"year\":2026,\"month\":6}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.fields.amount").value("Amount is required"));

        verifyNoInteractions(budgetService);
    }

    private Budget budget(String userId, String category, String amount, int year, int month) {
        return Budget.builder()
            .userid(userId)
            .category(category)
            .amount(new BigDecimal(amount))
            .budgetYear(year)
            .budgetMonth(month)
            .build();
    }

    record BudgetPayload(String category, Integer year, Integer month, BigDecimal amount) {}
}

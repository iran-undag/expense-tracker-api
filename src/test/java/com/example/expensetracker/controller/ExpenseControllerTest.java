package com.example.expensetracker.controller;

import com.example.expensetracker.dto.ExpenseCreateRequestDto;
import com.example.expensetracker.model.Expense;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.service.ExpenseService;
import com.example.expensetracker.service.ReceiptProcessor;
import com.example.expensetracker.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("dev")
class ExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExpenseService expenseService;

    @MockBean
    private ReceiptProcessor receiptProcessor;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CurrentUserService currentUserService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllExpenses_shouldReturnList() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        Expense expense = new Expense();
        expense.setDescription("Lunch");
        expense.setUserid("testuser");
        when(currentUserService.getUserId(authentication)).thenReturn("testuser");
        when(expenseService.getAllExpenses("testuser")).thenReturn(List.of(expense));

        mockMvc.perform(get("/api/expenses").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Lunch"))
                .andExpect(jsonPath("$[0].userid").value("testuser"))
                .andExpect(jsonPath("$[0].username").doesNotExist());
    }

    @Test
    void createExpense_shouldReturnSavedExpense() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        ExpenseCreateRequestDto request = ExpenseCreateRequestDto.builder()
                .description("Coffee")
                .amount(new BigDecimal("5.00"))
                .build();

        Expense expense = new Expense();
        expense.setDescription("Coffee");
        expense.setAmount(new BigDecimal("5.00"));
        expense.setUserid("testuser");

        when(currentUserService.getUserId(authentication)).thenReturn("testuser");
        when(expenseService.saveExpense(any(Expense.class))).thenReturn(expense);

        mockMvc.perform(post("/api/expenses").principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Coffee"))
                .andExpect(jsonPath("$.userid").value("testuser"))
                .andExpect(jsonPath("$.username").doesNotExist());

        var expenseCaptor = forClass(Expense.class);
        verify(expenseService).saveExpense(expenseCaptor.capture());
        assertThat(expenseCaptor.getValue().getUserid()).isEqualTo("testuser");
    }

    @Test
    void getExpenseById_shouldReturn404IfNotFound() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        when(currentUserService.getUserId(authentication)).thenReturn("testuser");
        when(expenseService.getExpenseById(1L, "testuser")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/expenses/1").principal(authentication))
                .andExpect(status().isNotFound());
    }
}

package com.example.expensetracker.controller;

import com.example.expensetracker.model.Expense;
import com.example.expensetracker.service.ExpenseService;
import com.example.expensetracker.service.ReceiptProcessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseController.class)
class ExpenseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExpenseService expenseService;

    @MockBean
    private ReceiptProcessor receiptProcessor;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllExpenses_shouldReturnList() throws Exception {
        Expense expense = new Expense();
        expense.setDescription("Lunch");
        when(expenseService.getAllExpenses()).thenReturn(List.of(expense));

        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].description").value("Lunch"));
    }

    @Test
    void createExpense_shouldReturnSavedExpense() throws Exception {
        Expense expense = new Expense();
        expense.setDescription("Coffee");
        expense.setAmount(new BigDecimal("5.00"));
        
        when(expenseService.saveExpense(any(Expense.class))).thenReturn(expense);

        mockMvc.perform(post("/api/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(expense)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Coffee"));
    }

    @Test
    void getExpenseById_shouldReturn404IfNotFound() throws Exception {
        when(expenseService.getExpenseById(1L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/expenses/1"))
                .andExpect(status().isNotFound());
    }
}

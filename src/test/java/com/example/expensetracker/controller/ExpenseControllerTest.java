package com.example.expensetracker.controller;

import com.example.expensetracker.dto.ExpenseCreateRequestDto;
import com.example.expensetracker.model.Expense;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.security.UserDataScope;
import com.example.expensetracker.service.ExpenseFilterCriteria;
import com.example.expensetracker.service.ExpenseService;
import com.example.expensetracker.service.ReceiptCategoryNormalizer;
import com.example.expensetracker.service.ReceiptProcessor;
import com.example.expensetracker.service.RecurringExpenseService;
import com.example.expensetracker.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExpenseController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("dev")
class ExpenseControllerTest {

    private static final UserDataScope SCOPE = UserDataScope.personal("testuser");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExpenseService expenseService;

    @MockBean
    private ReceiptProcessor receiptProcessor;

    @MockBean
    private ReceiptCategoryNormalizer receiptCategoryNormalizer;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private RecurringExpenseService recurringExpenseService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getAllExpenses_shouldReturnPagedList() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        Expense expense = new Expense();
        expense.setDescription("Lunch");
        expense.setUserid("testuser");
        when(currentUserService.getDataScope(authentication)).thenReturn(SCOPE);
        PageRequest pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "id"));
        when(expenseService.getAllExpenses(any(com.example.expensetracker.security.UserDataScope.class), any(), any()))
                .thenReturn(new PageImpl<>(List.of(expense), pageable, 1));

        mockMvc.perform(get("/api/expenses").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("Lunch"))
                .andExpect(jsonPath("$.content[0].userid").value("testuser"))
                .andExpect(jsonPath("$.content[0].username").doesNotExist())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void getAllExpenses_shouldPassFiltersToService() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        when(currentUserService.getDataScope(authentication)).thenReturn(SCOPE);
        PageRequest pageable = PageRequest.of(0, 5, Sort.by(Sort.Direction.DESC, "amount"));
        when(expenseService.getAllExpenses(any(com.example.expensetracker.security.UserDataScope.class), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        mockMvc.perform(get("/api/expenses")
                        .param("fromDate", "2026-06-01")
                        .param("toDate", "2026-06-30")
                        .param("category", "Food")
                        .param("minAmount", "10.00")
                        .param("maxAmount", "50.00")
                        .param("query", "lunch")
                        .param("page", "0")
                        .param("size", "5")
                        .param("sort", "amount,desc")
                        .principal(authentication))
                .andExpect(status().isOk());

        var filterCaptor = forClass(ExpenseFilterCriteria.class);
        var pageableCaptor = forClass(org.springframework.data.domain.Pageable.class);
        verify(expenseService).getAllExpenses(
                eq(SCOPE),
                filterCaptor.capture(),
                pageableCaptor.capture());

        ExpenseFilterCriteria filters = filterCaptor.getValue();
        assertThat(filters.fromDate()).isEqualTo(LocalDate.of(2026, 6, 1));
        assertThat(filters.toDate()).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(filters.category()).isEqualTo("Food");
        assertThat(filters.minAmount()).isEqualByComparingTo("10.00");
        assertThat(filters.maxAmount()).isEqualByComparingTo("50.00");
        assertThat(filters.query()).isEqualTo("lunch");
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("amount").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void getExpensesForMonth_shouldReturnPagedList() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        Expense expense = new Expense();
        expense.setDescription("Groceries");
        expense.setDate(LocalDate.of(2024, 5, 12));
        expense.setUserid("testuser");
        when(currentUserService.getDataScope(authentication)).thenReturn(SCOPE);
        PageRequest pageable = PageRequest.of(1, 5, Sort.by(Sort.Direction.DESC, "date"));
        when(expenseService.getExpensesForMonth(2024, 5, SCOPE, pageable))
                .thenReturn(new PageImpl<>(List.of(expense), pageable, 6));

        mockMvc.perform(get("/api/expenses/month/2024/5")
                        .param("page", "1")
                        .param("size", "5")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].description").value("Groceries"))
                .andExpect(jsonPath("$.content[0].date").value("2024-05-12"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void getAllExpenses_shouldReturn400ForInvalidSortProperty() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);

        mockMvc.perform(get("/api/expenses")
                        .param("sort", "string")
                        .principal(authentication))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(
                        "Invalid sort property 'string'")));

        verifyNoInteractions(expenseService);
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

        when(currentUserService.getDataScope(authentication)).thenReturn(SCOPE);
        when(expenseService.saveExpense(eq(SCOPE), any(Expense.class))).thenReturn(expense);

        mockMvc.perform(post("/api/expenses").principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Coffee"))
                .andExpect(jsonPath("$.userid").value("testuser"))
                .andExpect(jsonPath("$.username").doesNotExist());

        var expenseCaptor = forClass(Expense.class);
        verify(expenseService).saveExpense(eq(SCOPE), expenseCaptor.capture());
        assertThat(expenseCaptor.getValue().getUserid()).isNull();
    }

    @Test
    void createExpense_shouldReturn400WhenAmountIsMissing() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);

        mockMvc.perform(post("/api/expenses").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Coffee\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fields.amount").value("Amount is required"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void updateExpense_shouldReturn400WhenAmountIsMissing() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);

        mockMvc.perform(put("/api/expenses/1").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Coffee\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fields.amount").value("Amount is required"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void createExpense_shouldReturnGenericMessageForMalformedJson() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);

        mockMvc.perform(post("/api/expenses").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request"));

        verifyNoInteractions(expenseService);
    }

    @Test
    void processReceipt_shouldNormalizeCategoryBeforeReturningExpense() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        List<String> activeCategories = List.of("Food", "Other");
        MockMultipartFile image = new MockMultipartFile(
                "image",
                "receipt.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                new byte[]{1, 2, 3});
        Expense extractedExpense = Expense.builder()
                .description("Corner Cafe")
                .amount(new BigDecimal("18.25"))
                .date(LocalDate.of(2026, 7, 3))
                .category("Restaurant")
                .build();

        when(currentUserService.getDataScope(authentication)).thenReturn(SCOPE);
        when(receiptCategoryNormalizer.getActiveCategoryNames(SCOPE)).thenReturn(activeCategories);
        when(receiptProcessor.processReceipt(any(), eq(activeCategories))).thenReturn(extractedExpense);
        when(receiptCategoryNormalizer.normalize(extractedExpense, activeCategories)).thenReturn("Food");

        mockMvc.perform(multipart("/api/expenses/receipt")
                        .file(image)
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Corner Cafe"))
                .andExpect(jsonPath("$.category").value("Food"))
                .andExpect(jsonPath("$.userid").value("testuser"));

        verify(receiptProcessor).processReceipt(any(), eq(activeCategories));
        verify(receiptCategoryNormalizer).normalize(extractedExpense, activeCategories);
    }

    @Test
    void getExpenseById_shouldReturn404IfNotFound() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        when(currentUserService.getDataScope(authentication)).thenReturn(SCOPE);
        when(expenseService.getExpenseById(1L, SCOPE)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/expenses/1").principal(authentication))
                .andExpect(status().isNotFound());
    }
}

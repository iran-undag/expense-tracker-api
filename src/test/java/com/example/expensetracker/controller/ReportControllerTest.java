package com.example.expensetracker.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expensetracker.dto.CategoryBreakdownDto;
import com.example.expensetracker.dto.MonthlySummaryDto;
import com.example.expensetracker.dto.SpendingTrendDto;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.security.JwtTokenProvider;
import com.example.expensetracker.security.UserDataScope;
import com.example.expensetracker.service.RecurringExpenseService;
import com.example.expensetracker.service.ReportService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("dev")
class ReportControllerTest {

    private static final UserDataScope SCOPE = UserDataScope.personal("testuser");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportService reportService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private RecurringExpenseService recurringExpenseService;

    @Test
    void monthlySummary_shouldUseAuthenticatedUser() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        when(currentUserService.getDataScope(authentication)).thenReturn(SCOPE);
        when(reportService.getMonthlySummary(SCOPE, 2026, 6)).thenReturn(
            MonthlySummaryDto.builder()
                .year(2026)
                .month(6)
                .totalAmount(new BigDecimal("150.00"))
                .expenseCount(2L)
                .averageAmount(new BigDecimal("75.00"))
                .build()
        );

        mockMvc.perform(get("/api/reports/monthly-summary")
                .param("year", "2026")
                .param("month", "6")
                .principal(authentication))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalAmount").value(150.00))
            .andExpect(jsonPath("$.expenseCount").value(2))
            .andExpect(jsonPath("$.averageAmount").value(75.00));

        verify(reportService).getMonthlySummary(SCOPE, 2026, 6);
    }

    @Test
    void categoryBreakdown_shouldParseDateRange() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        when(currentUserService.getDataScope(authentication)).thenReturn(SCOPE);
        when(reportService.getCategoryBreakdown(
            SCOPE,
            LocalDate.of(2026, 6, 1),
            LocalDate.of(2026, 6, 30)
        )).thenReturn(List.of(CategoryBreakdownDto.builder()
            .category("Food")
            .amount(new BigDecimal("150.00"))
            .percentOfTotal(new BigDecimal("100.00"))
            .build()));

        mockMvc.perform(get("/api/reports/category-breakdown")
                .param("fromDate", "2026-06-01")
                .param("toDate", "2026-06-30")
                .principal(authentication))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].category").value("Food"))
            .andExpect(jsonPath("$[0].percentOfTotal").value(100.00));
    }

    @Test
    void spendingTrend_shouldReturnTrendRows() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        when(currentUserService.getDataScope(authentication)).thenReturn(SCOPE);
        when(reportService.getSpendingTrend(SCOPE, 2026, 6, 3, "Food")).thenReturn(List.of(
            SpendingTrendDto.builder().year(2026).month(4).totalAmount(BigDecimal.ZERO).build(),
            SpendingTrendDto.builder().year(2026).month(5).totalAmount(new BigDecimal("50.00")).build(),
            SpendingTrendDto.builder().year(2026).month(6).totalAmount(new BigDecimal("75.00")).build()
        ));

        mockMvc.perform(get("/api/reports/spending-trend")
                .param("year", "2026")
                .param("month", "6")
                .param("months", "3")
                .param("category", "Food")
                .principal(authentication))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].month").value(4))
            .andExpect(jsonPath("$[2].totalAmount").value(75.00));
    }

    @Test
    void spendingTrend_shouldReturn400ForInvalidMonths() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);

        mockMvc.perform(get("/api/reports/spending-trend")
                .param("year", "2026")
                .param("month", "6")
                .param("months", "25")
                .principal(authentication))
            .andExpect(status().isBadRequest());

        verifyNoInteractions(reportService);
    }
}

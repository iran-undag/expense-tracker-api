package com.example.expensetracker.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.expensetracker.demo.security.DemoFeatureGuard;
import com.example.expensetracker.demo.session.DemoSessionException;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.security.JwtTokenProvider;
import com.example.expensetracker.service.ImportExportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ImportExportController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("dev")
class ImportExportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ImportExportService importExportService;
    @MockBean private CurrentUserService currentUserService;
    @MockBean private DemoFeatureGuard demoFeatureGuard;
    @MockBean private JwtTokenProvider jwtTokenProvider;

    @Test
    void export_returnsDemoFeatureDisabledWithoutCallingServices() throws Exception {
        var authentication = new TestingAuthenticationToken("demo", null);
        org.mockito.Mockito.doThrow(DemoSessionException.featureDisabled())
            .when(demoFeatureGuard).requirePersonal(authentication);

        mockMvc.perform(get("/api/import-export/export")
                .param("fromDate", "2026-08-01")
                .param("toDate", "2026-08-14")
                .principal(authentication))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("DEMO_FEATURE_DISABLED"));

        verifyNoInteractions(importExportService, currentUserService);
    }

    @Test
    void import_returnsDemoFeatureDisabledWithoutCallingServices() throws Exception {
        var authentication = new TestingAuthenticationToken("demo", null);
        org.mockito.Mockito.doThrow(DemoSessionException.featureDisabled())
            .when(demoFeatureGuard).requirePersonal(authentication);

        mockMvc.perform(post("/api/import-export/import")
                .contentType("text/csv")
                .content("date,description,amount,category")
                .principal(authentication))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("DEMO_FEATURE_DISABLED"));

        verifyNoInteractions(importExportService, currentUserService);
    }

    @Test
    void export_remainsAvailableForPersonalUsers() throws Exception {
        var authentication = new TestingAuthenticationToken("personal", null);
        when(currentUserService.getUserId(authentication)).thenReturn("personal-owner");
        when(importExportService.exportExpensesCsv(
            "personal-owner", java.time.LocalDate.of(2026, 8, 1), java.time.LocalDate.of(2026, 8, 14)))
            .thenReturn("date,description,amount,category\n");

        mockMvc.perform(get("/api/import-export/export")
                .param("fromDate", "2026-08-01")
                .param("toDate", "2026-08-14")
                .principal(authentication))
            .andExpect(status().isOk());
    }
}

package com.example.expensetracker.controller;

import static org.mockito.ArgumentCaptor.forClass;
import static org.assertj.core.api.Assertions.assertThat;
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

import com.example.expensetracker.model.ExpenseCategory;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.security.JwtTokenProvider;
import com.example.expensetracker.security.UserDataScope;
import com.example.expensetracker.service.CategoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("dev")
class CategoryControllerTest {

    private static final UserDataScope SCOPE = UserDataScope.personal("testuser");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CategoryService categoryService;

    @MockBean
    private CurrentUserService currentUserService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void getCategories_shouldUseAuthenticatedUser() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        ExpenseCategory category = category("testuser", "Food", true);
        category.setId(1L);
        category.setSystemDefault(true);
        when(currentUserService.getDataScope(authentication)).thenReturn(SCOPE);
        when(categoryService.getCategories(SCOPE, false)).thenReturn(List.of(category));

        mockMvc.perform(get("/api/categories").principal(authentication))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].id").value(1))
            .andExpect(jsonPath("$[0].name").value("Food"))
            .andExpect(jsonPath("$[0].systemDefault").value(true))
            .andExpect(jsonPath("$[0].active").value(true))
            .andExpect(jsonPath("$[0].userid").value("testuser"));
    }

    @Test
    void createCategory_shouldPassCategoryWithoutRequestUserId() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        ExpenseCategory saved = category("testuser", "Pets", true);
        saved.setId(12L);
        when(currentUserService.getDataScope(authentication)).thenReturn(SCOPE);
        when(categoryService.createCategory(eq(SCOPE), any(ExpenseCategory.class))).thenReturn(saved);

        mockMvc.perform(post("/api/categories")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CategoryPayload("Pets", "#123456", "paw", true))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Pets"))
            .andExpect(jsonPath("$.userid").value("testuser"));

        var captor = forClass(ExpenseCategory.class);
        verify(categoryService).createCategory(eq(SCOPE), captor.capture());
        assertThat(captor.getValue().getUserid()).isNull();
        assertThat(captor.getValue().getName()).isEqualTo("Pets");
    }

    @Test
    void createCategory_shouldReturn400WhenNameMissing() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);

        mockMvc.perform(post("/api/categories")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Validation failed"))
            .andExpect(jsonPath("$.fields.name").value("Name is required"));

        verifyNoInteractions(categoryService);
    }

    @Test
    void updateCategory_shouldReturn404WhenMissing() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        when(currentUserService.getDataScope(authentication)).thenReturn(SCOPE);
        when(categoryService.updateCategory(eq(1L), eq(SCOPE), any(ExpenseCategory.class)))
            .thenThrow(new RuntimeException("missing"));

        mockMvc.perform(put("/api/categories/1")
                .principal(authentication)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new CategoryPayload("Pets", "#123456", "paw", true))))
            .andExpect(status().isNotFound());
    }

    @Test
    void deleteCategory_shouldReturnNoContent() throws Exception {
        Authentication authentication = new TestingAuthenticationToken("testuser", null);
        when(currentUserService.getDataScope(authentication)).thenReturn(SCOPE);

        mockMvc.perform(delete("/api/categories/1").principal(authentication))
            .andExpect(status().isNoContent());

        verify(categoryService).deleteCategory(1L, SCOPE);
    }

    private ExpenseCategory category(String userId, String name, boolean active) {
        return ExpenseCategory.builder()
            .userid(userId)
            .name(name)
            .color("#123456")
            .icon("tag")
            .active(active)
            .build();
    }

    record CategoryPayload(String name, String color, String icon, Boolean active) {}
}

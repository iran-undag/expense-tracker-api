package com.example.expensetracker.controller;

import com.example.expensetracker.dto.CategoryMapper;
import com.example.expensetracker.dto.CategoryRequestDto;
import com.example.expensetracker.dto.CategoryResponseDto;
import com.example.expensetracker.model.ExpenseCategory;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.service.CategoryService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<List<CategoryResponseDto>> getCategories(
        @RequestParam(defaultValue = "false") boolean includeInactive,
        Authentication authentication
    ) {
        String userId = currentUserService.getUserId(authentication);
        return ResponseEntity.ok(categoryService.getCategories(userId, includeInactive).stream()
            .map(CategoryMapper::toDto)
            .toList());
    }

    @PostMapping
    public ResponseEntity<CategoryResponseDto> createCategory(
        @Valid @RequestBody CategoryRequestDto request,
        Authentication authentication
    ) {
        String userId = currentUserService.getUserId(authentication);
        ExpenseCategory saved = categoryService.createCategory(userId, CategoryMapper.toEntity(request));
        return ResponseEntity.ok(CategoryMapper.toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponseDto> updateCategory(
        @PathVariable Long id,
        @Valid @RequestBody CategoryRequestDto request,
        Authentication authentication
    ) {
        try {
            String userId = currentUserService.getUserId(authentication);
            ExpenseCategory updated = categoryService.updateCategory(id, userId, CategoryMapper.toEntity(request));
            return ResponseEntity.ok(CategoryMapper.toDto(updated));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCategory(
        @PathVariable Long id,
        Authentication authentication
    ) {
        try {
            String userId = currentUserService.getUserId(authentication);
            categoryService.deleteCategory(id, userId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

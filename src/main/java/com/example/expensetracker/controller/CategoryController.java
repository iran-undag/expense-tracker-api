package com.example.expensetracker.controller;

import com.example.expensetracker.dto.CategoryMapper;
import com.example.expensetracker.dto.CategoryRequestDto;
import com.example.expensetracker.dto.CategoryResponseDto;
import com.example.expensetracker.demo.quota.DemoMutationExecutor;
import com.example.expensetracker.demo.session.DemoSessionException;
import com.example.expensetracker.model.ExpenseCategory;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.security.UserDataScope;
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
    private final DemoMutationExecutor mutationExecutor;

    @GetMapping
    public ResponseEntity<List<CategoryResponseDto>> getCategories(
        @RequestParam(defaultValue = "false") boolean includeInactive,
        Authentication authentication
    ) {
        UserDataScope scope = currentUserService.getDataScope(authentication);
        return ResponseEntity.ok(categoryService.getCategories(scope, includeInactive).stream()
            .map(CategoryMapper::toDto)
            .toList());
    }

    @PostMapping
    public ResponseEntity<CategoryResponseDto> createCategory(
        @Valid @RequestBody CategoryRequestDto request,
        Authentication authentication
    ) {
        UserDataScope scope = currentUserService.getDataScope(authentication);
        ExpenseCategory saved = mutationExecutor.execute(
            authentication, 1, () -> categoryService.createCategory(scope, CategoryMapper.toEntity(request)));
        return ResponseEntity.ok(CategoryMapper.toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponseDto> updateCategory(
        @PathVariable Long id,
        @Valid @RequestBody CategoryRequestDto request,
        Authentication authentication
    ) {
        try {
            UserDataScope scope = currentUserService.getDataScope(authentication);
            ExpenseCategory updated = mutationExecutor.execute(authentication, 1, () ->
                categoryService.updateCategory(id, scope, CategoryMapper.toEntity(request)));
            return ResponseEntity.ok(CategoryMapper.toDto(updated));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (DemoSessionException e) {
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
            UserDataScope scope = currentUserService.getDataScope(authentication);
            mutationExecutor.execute(authentication, 1, () -> {
                categoryService.deleteCategory(id, scope);
                return null;
            });
            return ResponseEntity.noContent().build();
        } catch (DemoSessionException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

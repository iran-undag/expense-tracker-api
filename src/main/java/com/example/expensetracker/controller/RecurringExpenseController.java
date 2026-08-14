package com.example.expensetracker.controller;

import com.example.expensetracker.dto.RecurringExpenseMapper;
import com.example.expensetracker.dto.RecurringExpenseRequestDto;
import com.example.expensetracker.dto.RecurringExpenseResponseDto;
import com.example.expensetracker.model.RecurringExpense;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.security.UserDataScope;
import com.example.expensetracker.service.RecurringExpenseService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/recurring-expenses")
@RequiredArgsConstructor
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public ResponseEntity<List<RecurringExpenseResponseDto>> getRecurringExpenses(Authentication authentication) {
        UserDataScope scope = currentUserService.getDataScope(authentication);
        recurringExpenseService.generateDueExpenses(scope, java.time.LocalDate.now());
        return ResponseEntity.ok(recurringExpenseService.getRecurringExpenses(scope).stream()
            .map(RecurringExpenseMapper::toDto)
            .toList());
    }

    @PostMapping
    public ResponseEntity<RecurringExpenseResponseDto> createRecurringExpense(
        @Valid @RequestBody RecurringExpenseRequestDto request,
        Authentication authentication
    ) {
        UserDataScope scope = currentUserService.getDataScope(authentication);
        RecurringExpense saved = recurringExpenseService.saveRecurringExpense(scope, RecurringExpenseMapper.toEntity(request));
        return ResponseEntity.ok(RecurringExpenseMapper.toDto(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringExpenseResponseDto> updateRecurringExpense(
        @PathVariable Long id,
        @Valid @RequestBody RecurringExpenseRequestDto request,
        Authentication authentication
    ) {
        try {
            UserDataScope scope = currentUserService.getDataScope(authentication);
            RecurringExpense updated = recurringExpenseService.updateRecurringExpense(id, scope, RecurringExpenseMapper.toEntity(request));
            return ResponseEntity.ok(RecurringExpenseMapper.toDto(updated));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRecurringExpense(@PathVariable Long id, Authentication authentication) {
        try {
            UserDataScope scope = currentUserService.getDataScope(authentication);
            recurringExpenseService.deleteRecurringExpense(id, scope);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }
}

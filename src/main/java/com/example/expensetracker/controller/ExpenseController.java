package com.example.expensetracker.controller;

import com.example.expensetracker.dto.ExpenseCreateRequestDto;
import com.example.expensetracker.dto.ExpenseMapper;
import com.example.expensetracker.dto.ExpenseResponseDto;
import com.example.expensetracker.model.Expense;
import com.example.expensetracker.service.ExpenseService;
import com.example.expensetracker.service.ReceiptProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Expense Management", description = "Endpoints for managing and analyzing expense records")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final ReceiptProcessor receiptProcessor;

    @PostMapping
    @Operation(summary = "Create a new expense", description = "Creates a manual expense entry in the database")
    @ApiResponse(responseCode = "200", description = "Expense created successfully")
    public ResponseEntity<ExpenseResponseDto> createExpense(@RequestBody ExpenseCreateRequestDto request, Principal principal) {
        log.info("Received request to create expense: {}", request);
        Expense expenseEntity = ExpenseMapper.toEntity(request);
        expenseEntity.setUserid(principal.getName());
        Expense savedExpense = expenseService.saveExpense(expenseEntity);
        return ResponseEntity.ok(ExpenseMapper.toDto(savedExpense));
    }

    @GetMapping
    @Operation(summary = "Get all expenses", description = "Returns a list of all expense records")
    public ResponseEntity<List<ExpenseResponseDto>> getAllExpenses(Principal principal) {
        return ResponseEntity.ok(ExpenseMapper.toDtoList(expenseService.getAllExpenses(principal.getName())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get expense by ID", description = "Returns a single expense record by its unique ID")
    @ApiResponse(responseCode = "200", description = "Found the expense")
    @ApiResponse(responseCode = "404", description = "Expense not found")
    public ResponseEntity<ExpenseResponseDto> getExpenseById(@PathVariable Long id, Principal principal) {
        return expenseService.getExpenseById(id, principal.getName())
                .map(ExpenseMapper::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an expense", description = "Updates an existing expense entry")
    public ResponseEntity<ExpenseResponseDto> updateExpense(@PathVariable Long id, @RequestBody ExpenseCreateRequestDto request, Principal principal) {
        try {
            Expense updatedExpense = expenseService.updateExpense(id, principal.getName(), ExpenseMapper.toEntity(request));
            return ResponseEntity.ok(ExpenseMapper.toDto(updatedExpense));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an expense", description = "Deletes an existing expense entry")
    public ResponseEntity<Void> deleteExpense(@PathVariable Long id, Principal principal) {
        try {
            expenseService.deleteExpense(id, principal.getName());
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<List<ExpenseResponseDto>> getExpensesByDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date, Principal principal) {
        return ResponseEntity.ok(ExpenseMapper.toDtoList(expenseService.getExpensesByDate(date, principal.getName())));
    }

    @GetMapping("/month/{year}/{month}/total")
    public ResponseEntity<BigDecimal> getTotalExpensesForMonth(
            @PathVariable int year,
            @PathVariable int month, Principal principal) {
        return ResponseEntity.ok(expenseService.getTotalExpensesForMonth(year, month, principal.getName()));
    }

    @PostMapping(value = "/receipt", consumes = "multipart/form-data")
    @Operation(summary = "Process receipt image", description = "Extracts expense data from a receipt image using AI")
    @ApiResponse(responseCode = "200", description = "Receipt processed successfully")
    public ResponseEntity<ExpenseResponseDto> processReceipt(
            @Parameter(description = "The receipt image file (JPG/PNG)") @RequestParam("image") MultipartFile image, Principal principal) {
        log.info("Received request to process receipt: {}", image.getOriginalFilename());
        Expense extractedExpense = receiptProcessor.processReceipt(image);
        extractedExpense.setUserid(principal.getName());
        log.info("Extracted expense details: {}", extractedExpense);
        return ResponseEntity.ok(ExpenseMapper.toDto(extractedExpense));
    }
}

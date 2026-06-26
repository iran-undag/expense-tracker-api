package com.example.expensetracker.controller;

import com.example.expensetracker.dto.ExpenseCreateRequestDto;
import com.example.expensetracker.dto.ExpenseMapper;
import com.example.expensetracker.dto.ExpenseResponseDto;
import com.example.expensetracker.dto.PageResponseDto;
import com.example.expensetracker.exception.InvalidSortPropertyException;
import com.example.expensetracker.model.Expense;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.service.ExpenseService;
import com.example.expensetracker.service.ReceiptProcessor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Slf4j
@Tag(
    name = "Expense Management",
    description = "Endpoints for managing and analyzing expense records"
)
@SecurityRequirement(name = "Bearer Authentication")
public class ExpenseController {

    private static final List<String> ALLOWED_SORT_PROPERTIES = List.of(
        "id",
        "description",
        "amount",
        "date",
        "category",
        "userid"
    );

    private final ExpenseService expenseService;
    private final ReceiptProcessor receiptProcessor;
    private final CurrentUserService currentUserService;

    @PostMapping
    @Operation(
        summary = "Create a new expense",
        description = "Creates a manual expense entry in the database"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Expense created successfully"
    )
    public ResponseEntity<ExpenseResponseDto> createExpense(
        @Valid @RequestBody ExpenseCreateRequestDto request,
        Authentication authentication
    ) {
        log.info("Received request to create expense: {}", request);
        String userId = currentUserService.getUserId(authentication);
        Expense expenseEntity = ExpenseMapper.toEntity(request);
        expenseEntity.setUserid(userId);
        Expense savedExpense = expenseService.saveExpense(expenseEntity);
        return ResponseEntity.ok(ExpenseMapper.toDto(savedExpense));
    }

    @GetMapping
    @Operation(
        summary = "Get all expenses",
        description = "Returns a list of all expense records"
    )
    public ResponseEntity<PageResponseDto<ExpenseResponseDto>> getAllExpenses(
        @PageableDefault(
            size = 10,
            sort = "id",
            direction = Sort.Direction.DESC
        ) Pageable pageable,
        Authentication authentication
    ) {
        validateSortProperties(pageable);
        String userId = currentUserService.getUserId(authentication);
        return ResponseEntity.ok(
            PageResponseDto.fromPage(
                expenseService.getAllExpenses(userId, pageable),
                ExpenseMapper::toDto
            )
        );
    }

    @GetMapping("/{id}")
    @Operation(
        summary = "Get expense by ID",
        description = "Returns a single expense record by its unique ID"
    )
    @ApiResponse(responseCode = "200", description = "Found the expense")
    @ApiResponse(responseCode = "404", description = "Expense not found")
    public ResponseEntity<ExpenseResponseDto> getExpenseById(
        @PathVariable Long id,
        Authentication authentication
    ) {
        String userId = currentUserService.getUserId(authentication);
        return expenseService
            .getExpenseById(id, userId)
            .map(ExpenseMapper::toDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @Operation(
        summary = "Update an expense",
        description = "Updates an existing expense entry"
    )
    public ResponseEntity<ExpenseResponseDto> updateExpense(
        @PathVariable Long id,
        @Valid @RequestBody ExpenseCreateRequestDto request,
        Authentication authentication
    ) {
        try {
            String userId = currentUserService.getUserId(authentication);
            Expense updatedExpense = expenseService.updateExpense(
                id,
                userId,
                ExpenseMapper.toEntity(request)
            );
            return ResponseEntity.ok(ExpenseMapper.toDto(updatedExpense));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete an expense",
        description = "Deletes an existing expense entry"
    )
    public ResponseEntity<Void> deleteExpense(
        @PathVariable Long id,
        Authentication authentication
    ) {
        try {
            String userId = currentUserService.getUserId(authentication);
            expenseService.deleteExpense(id, userId);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/date/{date}")
    public ResponseEntity<List<ExpenseResponseDto>> getExpensesByDate(
        @PathVariable @DateTimeFormat(
            iso = DateTimeFormat.ISO.DATE
        ) LocalDate date,
        Authentication authentication
    ) {
        String userId = currentUserService.getUserId(authentication);
        return ResponseEntity.ok(
            ExpenseMapper.toDtoList(
                expenseService.getExpensesByDate(date, userId)
            )
        );
    }

    @GetMapping("/month/{year}/{month}/total")
    public ResponseEntity<BigDecimal> getTotalExpensesForMonth(
        @PathVariable int year,
        @PathVariable int month,
        Authentication authentication
    ) {
        String userId = currentUserService.getUserId(authentication);
        return ResponseEntity.ok(
            expenseService.getTotalExpensesForMonth(year, month, userId)
        );
    }

    @GetMapping("/month/{year}/{month}")
    public ResponseEntity<
        PageResponseDto<ExpenseResponseDto>
    > getExpensesForMonth(
        @PathVariable int year,
        @PathVariable int month,
        @PageableDefault(
            size = 10,
            sort = "date",
            direction = Sort.Direction.DESC
        ) Pageable pageable,
        Authentication authentication
    ) {
        String userId = currentUserService.getUserId(authentication);
        return ResponseEntity.ok(
            PageResponseDto.fromPage(
                expenseService.getExpensesForMonth(
                    year,
                    month,
                    userId,
                    pageable
                ),
                ExpenseMapper::toDto
            )
        );
    }

    @PostMapping(value = "/receipt", consumes = "multipart/form-data")
    @Operation(
        summary = "Process receipt image",
        description = "Extracts expense data from a receipt image using AI"
    )
    @ApiResponse(
        responseCode = "200",
        description = "Receipt processed successfully"
    )
    public ResponseEntity<ExpenseResponseDto> processReceipt(
        @Parameter(
            description = "The receipt image file (JPG/PNG)"
        ) @RequestParam("image") MultipartFile image,
        Authentication authentication
    ) {
        log.info(
            "Received request to process receipt: {}",
            image.getOriginalFilename()
        );
        String userId = currentUserService.getUserId(authentication);
        Expense extractedExpense = receiptProcessor.processReceipt(image);
        extractedExpense.setUserid(userId);
        log.info("Extracted expense details: {}", extractedExpense);
        return ResponseEntity.ok(ExpenseMapper.toDto(extractedExpense));
    }

    private void validateSortProperties(Pageable pageable) {
        pageable.getSort().forEach(order -> {
            if (!ALLOWED_SORT_PROPERTIES.contains(order.getProperty())) {
                throw new InvalidSortPropertyException(
                    order.getProperty(),
                    ALLOWED_SORT_PROPERTIES
                );
            }
        });
    }
}

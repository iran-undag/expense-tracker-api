package com.example.expensetracker.service;

import com.example.expensetracker.dto.ExpenseCreateRequestDto;
import com.example.expensetracker.dto.ExpenseMapper;
import com.example.expensetracker.dto.ImportErrorDto;
import com.example.expensetracker.dto.ImportResultDto;
import com.example.expensetracker.model.Expense;
import com.example.expensetracker.repository.ExpenseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ImportExportServiceImpl implements ImportExportService {

    private static final String CSV_HEADER = "date,description,category,amount";

    private final ExpenseRepository expenseRepository;
    private final ExpenseService expenseService;

    @Override
    public String exportExpensesCsv(String userId, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            throw new IllegalArgumentException("Start date and end date are required");
        }
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }

        StringBuilder csv = new StringBuilder(CSV_HEADER).append('\n');
        expenseRepository.findByUseridAndDateBetweenOrderByDateAscIdAsc(userId, fromDate, toDate)
            .forEach(expense -> csv.append(toCsvRow(expense)).append('\n'));
        return csv.toString();
    }

    @Override
    @Transactional
    public ImportResultDto importExpensesCsv(String userId, String csv) {
        if (csv == null || csv.isBlank()) {
            throw new IllegalArgumentException("Import file is empty");
        }

        List<ImportErrorDto> errors = new ArrayList<>();
        List<List<String>> rows = parseCsv(csv);
        int importedExpenses = 0;
        int startIndex = hasHeader(rows) ? 1 : 0;

        for (int index = startIndex; index < rows.size(); index += 1) {
            List<String> row = rows.get(index);
            if (row.stream().allMatch(String::isBlank)) {
                continue;
            }

            try {
                ExpenseCreateRequestDto expense = toExpense(row);
                validateExpense(expense);
                var entity = ExpenseMapper.toEntity(expense);
                entity.setUserid(userId);
                expenseService.saveExpense(entity);
                importedExpenses += 1;
            } catch (RuntimeException e) {
                errors.add(error(index, e.getMessage()));
            }
        }

        return ImportResultDto.builder()
            .importedExpenses(importedExpenses)
            .importedBudgets(0)
            .importedCategories(0)
            .errors(errors)
            .build();
    }

    private ExpenseCreateRequestDto toExpense(List<String> row) {
        if (row.size() < 4) {
            throw new IllegalArgumentException("CSV row must include date, description, category, and amount");
        }

        return ExpenseCreateRequestDto.builder()
            .date(parseDate(row.get(0)))
            .description(optionalText(row.get(1)))
            .category(optionalText(row.get(2)))
            .amount(parseAmount(row.get(3)))
            .build();
    }

    private void validateExpense(ExpenseCreateRequestDto expense) {
        if (expense.getAmount() == null) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (expense.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero");
        }
    }

    private LocalDate parseDate(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return LocalDate.parse(trimmed);
    }

    private BigDecimal parseAmount(String value) {
        String trimmed = value == null ? "" : value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return new BigDecimal(trimmed);
    }

    private String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean hasHeader(List<List<String>> rows) {
        if (rows.isEmpty() || rows.get(0).size() < 4) {
            return false;
        }

        List<String> first = rows.get(0);
        return "date".equalsIgnoreCase(first.get(0).trim())
            && "description".equalsIgnoreCase(first.get(1).trim())
            && "category".equalsIgnoreCase(first.get(2).trim())
            && "amount".equalsIgnoreCase(first.get(3).trim());
    }

    private String toCsvRow(Expense expense) {
        return String.join(",",
            escapeCsv(expense.getDate() == null ? "" : expense.getDate().toString()),
            escapeCsv(expense.getDescription()),
            escapeCsv(expense.getCategory()),
            escapeCsv(expense.getAmount() == null ? "" : expense.getAmount().toPlainString())
        );
    }

    private String escapeCsv(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\n") || safe.contains("\r")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private List<List<String>> parseCsv(String csv) {
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;

        for (int index = 0; index < csv.length(); index += 1) {
            char current = csv.charAt(index);
            if (current == '"') {
                if (inQuotes && index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                    field.append('"');
                    index += 1;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (current == ',' && !inQuotes) {
                row.add(field.toString());
                field.setLength(0);
            } else if ((current == '\n' || current == '\r') && !inQuotes) {
                if (current == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') {
                    index += 1;
                }
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
            } else {
                field.append(current);
            }
        }

        row.add(field.toString());
        if (!(row.size() == 1 && row.get(0).isEmpty())) {
            rows.add(row);
        }
        return rows;
    }

    private ImportErrorDto error(int index, String message) {
        return ImportErrorDto.builder()
            .section("expenses")
            .index(index)
            .message(message == null ? "Invalid row" : message)
            .build();
    }
}

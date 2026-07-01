package com.example.expensetracker.controller;

import com.example.expensetracker.dto.ImportResultDto;
import com.example.expensetracker.security.CurrentUserService;
import com.example.expensetracker.service.ImportExportService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/import-export")
@RequiredArgsConstructor
public class ImportExportController {

    private final ImportExportService importExportService;
    private final CurrentUserService currentUserService;

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> exportData(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
        Authentication authentication
    ) {
        String userId = currentUserService.getUserId(authentication);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"expense-records.csv\"")
            .contentType(new MediaType("text", "csv"))
            .body(importExportService.exportExpensesCsv(userId, fromDate, toDate));
    }

    @PostMapping(value = "/import", consumes = "text/csv")
    public ResponseEntity<ImportResultDto> importData(
        @RequestBody String csv,
        Authentication authentication
    ) {
        String userId = currentUserService.getUserId(authentication);
        return ResponseEntity.ok(importExportService.importExpensesCsv(userId, csv));
    }
}

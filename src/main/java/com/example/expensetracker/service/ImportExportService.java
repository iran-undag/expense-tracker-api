package com.example.expensetracker.service;

import com.example.expensetracker.dto.ImportResultDto;
import java.time.LocalDate;

public interface ImportExportService {
    String exportExpensesCsv(String userId, LocalDate fromDate, LocalDate toDate);
    ImportResultDto importExpensesCsv(String userId, String csv);
}

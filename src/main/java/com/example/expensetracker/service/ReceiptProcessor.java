package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ReceiptProcessor {
    Expense processReceipt(MultipartFile image, List<String> allowedCategories);

    default Expense processReceipt(MultipartFile image) {
        return processReceipt(image, List.of());
    }
}

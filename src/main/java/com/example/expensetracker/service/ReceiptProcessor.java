package com.example.expensetracker.service;

import com.example.expensetracker.model.Expense;
import org.springframework.web.multipart.MultipartFile;

public interface ReceiptProcessor {
    Expense processReceipt(MultipartFile image);
}

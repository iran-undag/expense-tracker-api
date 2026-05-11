package com.example.expensetracker.service;

import com.azure.ai.documentintelligence.DocumentIntelligenceClient;
import com.azure.ai.documentintelligence.DocumentIntelligenceClientBuilder;
import com.azure.ai.documentintelligence.models.AnalyzeDocumentOptions;
import com.azure.ai.documentintelligence.models.AnalyzeOperationDetails;
import com.azure.ai.documentintelligence.models.AnalyzeResult;
import com.azure.ai.documentintelligence.models.AnalyzedDocument;
import com.azure.ai.documentintelligence.models.DocumentField;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.util.BinaryData;
import com.azure.core.util.polling.SyncPoller;
import com.example.expensetracker.exception.ReceiptProcessingException;
import com.example.expensetracker.model.Expense;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Slf4j
public class AzureDocumentReceiptProcessor implements ReceiptProcessor {

    private final DocumentIntelligenceClient client;

    public AzureDocumentReceiptProcessor(String endpoint, String key) {
        this.client = new DocumentIntelligenceClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(key))
                .buildClient();
    }

    @Override
    public Expense processReceipt(MultipartFile image) {
        log.info("Connecting to Azure AI processor for receipt: {}", image.getOriginalFilename());
        try {
            AnalyzeDocumentOptions options = new AnalyzeDocumentOptions(BinaryData.fromBytes(image.getBytes()));
            
            SyncPoller<AnalyzeOperationDetails, AnalyzeResult> poller = client.beginAnalyzeDocument(
                    "prebuilt-receipt",
                    options);

            AnalyzeResult result = poller.getFinalResult();
            log.info("Azure AI processing completed.");

            if (result.getDocuments() == null || result.getDocuments().isEmpty()) {
                throw new ReceiptProcessingException("No documents found in the receipt image.");
            }

            AnalyzedDocument document = result.getDocuments().get(0);
            Map<String, DocumentField> fields = document.getFields();

            String merchantName = fields.containsKey("MerchantName") ? fields.get("MerchantName").getContent() : "Unknown Merchant";
            BigDecimal total = fields.containsKey("Total") ? BigDecimal.valueOf(fields.get("Total").getValueNumber()) : BigDecimal.ZERO;
            LocalDate date = fields.containsKey("TransactionDate") ? fields.get("TransactionDate").getValueDate() : LocalDate.now();
            String category = fields.containsKey("Category") ? fields.get("Category").getContent() : "General";

            Expense extracted = Expense.builder()
                    .description(merchantName)
                    .amount(total)
                    .date(date)
                    .category(category)
                    .build();
            
            log.debug("Extracted data from Azure: {}", extracted);
            return extracted;

        } catch (IOException e) {
            throw new ReceiptProcessingException("Failed to read receipt image", e);
        }
    }
}

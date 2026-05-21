package com.example.expensetracker.service;

import com.azure.ai.documentintelligence.DocumentIntelligenceClient;
import com.azure.ai.documentintelligence.models.AnalyzeDocumentOptions;
import com.azure.ai.documentintelligence.models.AnalyzeOperationDetails;
import com.azure.ai.documentintelligence.models.AnalyzeResult;
import com.azure.ai.documentintelligence.models.AnalyzedDocument;
import com.azure.ai.documentintelligence.models.CurrencyValue;
import com.azure.ai.documentintelligence.models.DocumentField;
import com.azure.core.util.polling.SyncPoller;
import com.example.expensetracker.model.Expense;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AzureDocumentReceiptProcessorTest {

    @Mock
    private DocumentIntelligenceClient client;

    @Mock
    private SyncPoller<AnalyzeOperationDetails, AnalyzeResult> poller;

    @Mock
    private AnalyzeResult analyzeResult;

    @Mock
    private AnalyzedDocument analyzedDocument;

    @Mock
    private MultipartFile multipartFile;

    private AzureDocumentReceiptProcessor processor;

    @BeforeEach
    void setUp() throws IOException {
        MockitoAnnotations.openMocks(this);
        processor = new AzureDocumentReceiptProcessor(client);

        when(multipartFile.getBytes()).thenReturn(new byte[]{1, 2, 3});
        when(multipartFile.getOriginalFilename()).thenReturn("test-receipt.jpg");

        when(client.beginAnalyzeDocument(eq("prebuilt-receipt"), any(AnalyzeDocumentOptions.class)))
                .thenReturn(poller);
        when(poller.getFinalResult()).thenReturn(analyzeResult);
    }

    @Test
    void processReceipt_withCurrencyTotal_shouldExtractCorrectly() {
        // Arrange
        when(analyzeResult.getDocuments()).thenReturn(List.of(analyzedDocument));
        Map<String, DocumentField> fields = new HashMap<>();

        // MerchantName
        DocumentField merchantField = mock(DocumentField.class);
        when(merchantField.getContent()).thenReturn("Starbucks");
        fields.put("MerchantName", merchantField);

        // Total as Currency
        DocumentField totalField = mock(DocumentField.class);
        CurrencyValue currencyValue = mock(CurrencyValue.class);
        when(currencyValue.getAmount()).thenReturn(15.50);
        when(totalField.getValueCurrency()).thenReturn(currencyValue);
        when(totalField.getValueNumber()).thenReturn(null); // Simulated null to verify currency branch
        fields.put("Total", totalField);

        // TransactionDate
        DocumentField dateField = mock(DocumentField.class);
        LocalDate transactionDate = LocalDate.of(2026, 5, 21);
        when(dateField.getValueDate()).thenReturn(transactionDate);
        fields.put("TransactionDate", dateField);

        // Category
        DocumentField categoryField = mock(DocumentField.class);
        when(categoryField.getContent()).thenReturn("Food");
        fields.put("Category", categoryField);

        when(analyzedDocument.getFields()).thenReturn(fields);

        // Act
        Expense expense = processor.processReceipt(multipartFile);

        // Assert
        assertThat(expense).isNotNull();
        assertThat(expense.getDescription()).isEqualTo("Starbucks");
        assertThat(expense.getAmount()).isEqualByComparingTo(new BigDecimal("15.50"));
        assertThat(expense.getDate()).isEqualTo(transactionDate);
        assertThat(expense.getCategory()).isEqualTo("Food");
    }

    @Test
    void processReceipt_withNumberTotal_shouldExtractCorrectly() {
        // Arrange
        when(analyzeResult.getDocuments()).thenReturn(List.of(analyzedDocument));
        Map<String, DocumentField> fields = new HashMap<>();

        // Total as Number (e.g. if parsed without currency symbol)
        DocumentField totalField = mock(DocumentField.class);
        when(totalField.getValueCurrency()).thenReturn(null);
        when(totalField.getValueNumber()).thenReturn(25.75);
        fields.put("Total", totalField);

        when(analyzedDocument.getFields()).thenReturn(fields);

        // Act
        Expense expense = processor.processReceipt(multipartFile);

        // Assert
        assertThat(expense).isNotNull();
        assertThat(expense.getDescription()).isEqualTo("Unknown Merchant");
        assertThat(expense.getAmount()).isEqualByComparingTo(new BigDecimal("25.75"));
        assertThat(expense.getCategory()).isEqualTo("General");
    }

    @Test
    void processReceipt_withNullOrMissingFields_shouldHandleGracefully() {
        // Arrange
        when(analyzeResult.getDocuments()).thenReturn(List.of(analyzedDocument));
        Map<String, DocumentField> fields = new HashMap<>();
        
        // Fields map is present but lacks expected keys, or maps value to null
        fields.put("MerchantName", null);
        fields.put("Total", null);
        fields.put("TransactionDate", null);

        when(analyzedDocument.getFields()).thenReturn(fields);

        // Act
        Expense expense = processor.processReceipt(multipartFile);

        // Assert
        assertThat(expense).isNotNull();
        assertThat(expense.getDescription()).isEqualTo("Unknown Merchant");
        assertThat(expense.getAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(expense.getDate()).isEqualTo(LocalDate.now());
        assertThat(expense.getCategory()).isEqualTo("General");
    }
}

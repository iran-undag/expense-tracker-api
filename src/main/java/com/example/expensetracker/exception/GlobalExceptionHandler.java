package com.example.expensetracker.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    private final DataSize maxFileSize;

    public GlobalExceptionHandler(@Value("${spring.servlet.multipart.max-file-size:1MB}") String maxFileSize) {
        this.maxFileSize = DataSize.parse(maxFileSize);
    }

    @ExceptionHandler(ReceiptProcessingException.class)
    public ResponseEntity<Object> handleReceiptProcessingException(ReceiptProcessingException ex) {
        log.error("Receipt processing error: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Receipt Processing Failed");
        body.put("message", ex.getMessage());

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        log.warn("Maximum upload size exceeded: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.PAYLOAD_TOO_LARGE.value());
        body.put("error", "Payload Too Large");
        body.put("message", "Max file size exceeded :  " + formatDataSize(maxFileSize));

        return new ResponseEntity<>(body, HttpStatus.PAYLOAD_TOO_LARGE);
    }

    @ExceptionHandler(org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Object> handleHttpMessageNotReadableException(org.springframework.http.converter.HttpMessageNotReadableException ex) {
        log.error("Malformed JSON request: {}", ex.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.BAD_REQUEST.value());
        body.put("error", "Bad Request");
        body.put("message", "Malformed JSON request: " + ex.getMostSpecificCause().getMessage());

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleGeneralException(Exception ex) {
        log.error("Unhandled exception occurred: ", ex);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value());
        body.put("error", "Internal Server Error");
        body.put("message", "An unexpected error occurred");

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String formatDataSize(DataSize dataSize) {
        long bytes = dataSize.toBytes();
        if (bytes % DataSize.ofGigabytes(1).toBytes() == 0) {
            return bytes / DataSize.ofGigabytes(1).toBytes() + "GB";
        }
        if (bytes % DataSize.ofMegabytes(1).toBytes() == 0) {
            return bytes / DataSize.ofMegabytes(1).toBytes() + "MB";
        }
        if (bytes % DataSize.ofKilobytes(1).toBytes() == 0) {
            return bytes / DataSize.ofKilobytes(1).toBytes() + "KB";
        }
        return bytes + "B";
    }
}

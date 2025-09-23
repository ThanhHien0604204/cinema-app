package com.ntth.spring_boot_heroku_cinema_app.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now().getEpochSecond(),
                HttpStatus.BAD_REQUEST.value(),
                "Validation Error",
                "Request validation failed",
                errors
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(
            org.springframework.web.server.ResponseStatusException ex) {

        ErrorResponse errorResponse = new ErrorResponse(
                Instant.now().getEpochSecond(),
                ex.getStatusCode().value(),  // Lấy mã trạng thái (e.g., 400, 404)
                ex.getReason(),              // Lấy lý do lỗi (e.g., "Room not found")
                ex.getMessage(),             // Lấy thông điệp chi tiết
                Map.of()                     // Không có chi tiết bổ sung
        );

        return new ResponseEntity<>(errorResponse, ex.getStatusCode());
    }
}

// ErrorResponse DTO
record ErrorResponse(
        long timestamp,
        int status,
        String error,
        String message,
        Map<String, String> details
) {}
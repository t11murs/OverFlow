package com.orderflow.inventory.adapter.inbound.rest;

import com.orderflow.inventory.api.InventoryDtos.ApiErrorResponse;
import com.orderflow.inventory.api.InventoryDtos.FieldViolation;
import com.orderflow.inventory.domain.shared.DomainRuleViolation;
import com.orderflow.inventory.exception.CatalogDependencyException;
import com.orderflow.inventory.exception.InventoryConflictException;
import com.orderflow.inventory.exception.InventoryNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.Clock;
import java.util.List;

@RestControllerAdvice
public class InventoryExceptionHandler {

    private final Clock clock;

    public InventoryExceptionHandler(Clock clock) { this.clock = clock; }

    @ExceptionHandler(InventoryNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(InventoryNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(InventoryConflictException.class)
    ResponseEntity<ApiErrorResponse> conflict(InventoryConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(DomainRuleViolation.class)
    ResponseEntity<ApiErrorResponse> domain(DomainRuleViolation exception, HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(CatalogDependencyException.class)
    ResponseEntity<ApiErrorResponse> catalogDependency(
            CatalogDependencyException exception,
            HttpServletRequest request
    ) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> invalidBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage())).toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> constraint(ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(error -> new FieldViolation(error.getPropertyPath().toString(), error.getMessage())).toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, violations);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> malformed(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request contains an invalid value", request, List.of());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<ApiErrorResponse> dataConflict(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT", "Operation conflicts with current inventory data", request, List.of());
    }

    private ResponseEntity<ApiErrorResponse> response(
            HttpStatus status,
            String code,
            String message,
            HttpServletRequest request,
            List<FieldViolation> violations
    ) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                clock.instant(), status.value(), code, message, request.getRequestURI(), violations));
    }
}


package com.orderflow.order.adapter.inbound.rest;

import com.orderflow.order.api.OrderDtos.ApiErrorResponse;
import com.orderflow.order.api.OrderDtos.FieldViolation;
import com.orderflow.order.domain.shared.DomainRuleViolation;
import com.orderflow.order.exception.CatalogDependencyException;
import com.orderflow.order.exception.OrderConflictException;
import com.orderflow.order.exception.OrderNotFoundException;
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
public class OrderExceptionHandler {

    private final Clock clock;

    public OrderExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(OrderNotFoundException.class)
    ResponseEntity<ApiErrorResponse> handleNotFound(OrderNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(OrderConflictException.class)
    ResponseEntity<ApiErrorResponse> handleConflict(OrderConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(DomainRuleViolation.class)
    ResponseEntity<ApiErrorResponse> handleDomainViolation(DomainRuleViolation exception, HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(CatalogDependencyException.class)
    ResponseEntity<ApiErrorResponse> handleCatalogFailure(CatalogDependencyException exception, HttpServletRequest request) {
        return response(HttpStatus.SERVICE_UNAVAILABLE, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleInvalidBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(error -> new FieldViolation(error.getPropertyPath().toString(), error.getMessage()))
                .toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, violations);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiErrorResponse> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request contains an invalid value", request, List.of());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<ApiErrorResponse> handleDataConflict(Exception exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT", "Operation conflicts with current order data", request, List.of());
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


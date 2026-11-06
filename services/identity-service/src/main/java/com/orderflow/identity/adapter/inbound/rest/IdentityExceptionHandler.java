package com.orderflow.identity.adapter.inbound.rest;

import com.orderflow.identity.api.IdentityDtos.ApiErrorResponse;
import com.orderflow.identity.api.IdentityDtos.FieldViolation;
import com.orderflow.identity.exception.IdentityAuthenticationException;
import com.orderflow.identity.exception.IdentityConflictException;
import com.orderflow.identity.exception.IdentityNotFoundException;
import com.orderflow.identity.exception.IdentityValidationException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.util.List;

@RestControllerAdvice
public class IdentityExceptionHandler {

    private final Clock clock;

    public IdentityExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(IdentityAuthenticationException.class)
    ResponseEntity<ApiErrorResponse> authentication(IdentityAuthenticationException exception, HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(IdentityConflictException.class)
    ResponseEntity<ApiErrorResponse> conflict(IdentityConflictException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(IdentityNotFoundException.class)
    ResponseEntity<ApiErrorResponse> notFound(IdentityNotFoundException exception, HttpServletRequest request) {
        return response(HttpStatus.NOT_FOUND, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> accessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "Operation is not permitted", request, List.of());
    }

    @ExceptionHandler(IdentityValidationException.class)
    ResponseEntity<ApiErrorResponse> validation(IdentityValidationException exception, HttpServletRequest request) {
        return response(HttpStatus.UNPROCESSABLE_ENTITY, exception.code(), exception.getMessage(), request, List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> invalidBody(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<FieldViolation> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage())).toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> invalidParameters(ConstraintViolationException exception, HttpServletRequest request) {
        List<FieldViolation> violations = exception.getConstraintViolations().stream()
                .map(error -> new FieldViolation(error.getPropertyPath().toString(), error.getMessage())).toList();
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Request validation failed", request, violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiErrorResponse> malformed(HttpMessageNotReadableException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request contains an invalid value", request, List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> dataConflict(DataIntegrityViolationException exception, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "IDENTITY_DATA_CONFLICT", "Identity data conflicts with an existing resource", request, List.of());
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message,
                                                       HttpServletRequest request, List<FieldViolation> violations) {
        return ResponseEntity.status(status).body(new ApiErrorResponse(
                clock.instant(), status.value(), code, message, request.getRequestURI(), violations));
    }
}

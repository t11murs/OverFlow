package com.orderflow.identity.exception;

public class IdentityValidationException extends RuntimeException {
    private final String code;

    public IdentityValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}

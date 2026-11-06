package com.orderflow.identity.exception;

public class IdentityConflictException extends RuntimeException {
    private final String code;

    public IdentityConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}

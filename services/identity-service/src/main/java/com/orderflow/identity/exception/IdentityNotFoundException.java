package com.orderflow.identity.exception;

public class IdentityNotFoundException extends RuntimeException {
    private final String code;

    public IdentityNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}

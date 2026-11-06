package com.orderflow.catalog.exception;

public final class CatalogConflictException extends RuntimeException {

    private final String code;

    public CatalogConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}


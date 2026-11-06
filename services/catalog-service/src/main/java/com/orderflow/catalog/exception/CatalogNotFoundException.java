package com.orderflow.catalog.exception;

public final class CatalogNotFoundException extends RuntimeException {

    private final String code;

    public CatalogNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}


package com.orderflow.order.exception;

public final class CatalogDependencyException extends RuntimeException {

    private final String code;

    public CatalogDependencyException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}


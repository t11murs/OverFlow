package com.orderflow.inventory.exception;

public final class InventoryNotFoundException extends RuntimeException {

    private final String code;

    public InventoryNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}


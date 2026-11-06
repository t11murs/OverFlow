package com.orderflow.inventory.exception;

public final class InventoryConflictException extends RuntimeException {

    private final String code;

    public InventoryConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() { return code; }
}


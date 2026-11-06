package com.orderflow.order.exception;

public final class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException() {
        super("Order was not found");
    }
}


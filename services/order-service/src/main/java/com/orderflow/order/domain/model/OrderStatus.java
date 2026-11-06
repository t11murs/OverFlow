package com.orderflow.order.domain.model;

public enum OrderStatus {
    CREATED,
    RESERVING_INVENTORY,
    AWAITING_PAYMENT,
    CONFIRMED,
    CANCELLATION_PENDING,
    COMPENSATION_FAILED,
    CANCELLED
}


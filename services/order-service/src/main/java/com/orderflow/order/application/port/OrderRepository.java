package com.orderflow.order.application.port;

import com.orderflow.order.domain.model.CustomerOrder;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository {

    Optional<CustomerOrder> findById(UUID id);

    Optional<CustomerOrder> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey);

    PageResult<OrderSummary> findByCustomerId(UUID customerId, int page, int size);

    CustomerOrder save(CustomerOrder order);
}


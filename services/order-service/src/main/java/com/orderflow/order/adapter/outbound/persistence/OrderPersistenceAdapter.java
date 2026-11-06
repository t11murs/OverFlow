package com.orderflow.order.adapter.outbound.persistence;

import com.orderflow.order.application.port.OrderRepository;
import com.orderflow.order.application.port.OrderSummary;
import com.orderflow.order.application.port.PageResult;
import com.orderflow.order.domain.model.CustomerOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class OrderPersistenceAdapter implements OrderRepository {

    private final CustomerOrderJpaRepository repository;

    OrderPersistenceAdapter(CustomerOrderJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CustomerOrder> findById(UUID id) {
        return repository.findDetailedById(id).map(CustomerOrderEntity::toDomain);
    }

    @Override
    public Optional<CustomerOrder> findByCustomerIdAndIdempotencyKey(UUID customerId, String idempotencyKey) {
        return repository.findDetailedByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                .map(CustomerOrderEntity::toDomain);
    }

    @Override
    public PageResult<OrderSummary> findByCustomerId(UUID customerId, int page, int size) {
        Page<CustomerOrderEntity> result = repository.findByCustomerId(
                customerId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new PageResult<>(
                result.getContent().stream().map(entity -> new OrderSummary(
                        entity.id(),
                        entity.customerId(),
                        entity.status(),
                        entity.totalAmount(),
                        entity.currency(),
                        entity.createdAt(),
                        entity.updatedAt())).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public CustomerOrder save(CustomerOrder order) {
        CustomerOrderEntity entity = repository.findDetailedById(order.id())
                .orElseGet(() -> new CustomerOrderEntity(order));
        entity.updateFrom(order);
        return repository.saveAndFlush(entity).toDomain();
    }
}


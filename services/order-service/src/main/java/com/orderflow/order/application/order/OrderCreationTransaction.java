package com.orderflow.order.application.order;

import com.orderflow.order.application.port.OrderRepository;
import com.orderflow.order.application.port.OrderProcessPort;
import com.orderflow.order.application.port.OutboxMessagePort;
import com.orderflow.order.domain.model.CustomerOrder;
import com.orderflow.order.domain.model.OrderItem;
import com.orderflow.order.exception.OrderConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Service
public class OrderCreationTransaction {

    private final OrderRepository repository;
    private final OrderProcessPort process;
    private final OutboxMessagePort outbox;
    private final String inventoryCommandsTopic;

    public OrderCreationTransaction(
            OrderRepository repository,
            OrderProcessPort process,
            OutboxMessagePort outbox,
            @Value("${messaging.topics.inventory-commands}") String inventoryCommandsTopic
    ) {
        this.repository = repository;
        this.process = process;
        this.outbox = outbox;
        this.inventoryCommandsTopic = inventoryCommandsTopic;
    }

    @Transactional(readOnly = true)
    public Optional<CustomerOrder> findExisting(UUID customerId, String idempotencyKey, String fingerprint) {
        return repository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                .map(order -> requireSameRequest(order, fingerprint));
    }

    @Transactional
    public CustomerOrder saveIfAbsent(CustomerOrder candidate) {
        return repository.findByCustomerIdAndIdempotencyKey(
                        candidate.customerId(), candidate.idempotencyKey())
                .map(order -> requireSameRequest(order, candidate.requestFingerprint()))
                .orElseGet(() -> saveAndStartReservation(candidate));
    }

    private CustomerOrder saveAndStartReservation(CustomerOrder candidate) {
        candidate.startInventoryReservation(candidate.updatedAt());
        CustomerOrder saved = repository.save(candidate);
        process.start(saved.id(), saved.updatedAt());
        List<ReserveInventoryLine> items = saved.sellerOrders().stream()
                .flatMap(sellerOrder -> sellerOrder.items().stream())
                .map(OrderCreationTransaction::toReservationLine)
                .toList();
        outbox.append(
                "CustomerOrder",
                saved.id(),
                "ReserveInventory",
                1,
                inventoryCommandsTopic,
                saved.id().toString(),
                saved.id(),
                null,
                new ReserveInventoryPayload(saved.id(), items));
        return saved;
    }

    private static ReserveInventoryLine toReservationLine(OrderItem item) {
        return new ReserveInventoryLine(item.id(), item.offerId(), item.sellerId(), item.quantity());
    }

    private static CustomerOrder requireSameRequest(CustomerOrder order, String fingerprint) {
        if (!order.requestFingerprint().equals(fingerprint)) {
            throw new OrderConflictException(
                    "IDEMPOTENCY_KEY_REUSED", "Idempotency key was already used with another checkout payload");
        }
        return order;
    }

    public record ReserveInventoryPayload(UUID orderId, List<ReserveInventoryLine> items) {}

    public record ReserveInventoryLine(UUID orderItemId, UUID offerId, UUID sellerId, long quantity) {}
}

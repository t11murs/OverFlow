package com.orderflow.order.application.order;

import com.orderflow.order.application.port.OrderProcessPort;
import com.orderflow.order.application.port.OrderRepository;
import com.orderflow.order.application.port.OutboxMessagePort;
import com.orderflow.order.domain.model.CustomerOrder;
import com.orderflow.order.domain.model.OrderStatus;
import com.orderflow.order.exception.OrderNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class OrderCancellationCoordinator {

    private final OrderRepository orders;
    private final OrderProcessPort process;
    private final OutboxMessagePort outbox;
    private final Clock clock;
    private final String paymentCommandsTopic;

    public OrderCancellationCoordinator(
            OrderRepository orders,
            OrderProcessPort process,
            OutboxMessagePort outbox,
            Clock clock,
            @Value("${messaging.topics.payment-commands}") String paymentCommandsTopic
    ) {
        this.orders = orders;
        this.process = process;
        this.outbox = outbox;
        this.clock = clock;
        this.paymentCommandsTopic = paymentCommandsTopic;
    }

    @Transactional
    public CustomerOrder request(UUID orderId, UUID customerId) {
        CustomerOrder order = orders.findById(orderId).orElseThrow(OrderNotFoundException::new);
        if (!order.customerId().equals(customerId)) throw new OrderNotFoundException();
        OrderStatus previousStatus = order.status();
        Instant now = clock.instant();
        order.requestCancellation(now);
        CustomerOrder saved = orders.save(order);
        if (previousStatus == OrderStatus.CREATED || previousStatus == OrderStatus.CANCELLED) {
            return saved;
        }
        if (process.requestCancellation(order.id(), now) == OrderProcessPort.CancellationAction.REFUND_PAYMENT) {
            outbox.append(
                    "CustomerOrder", order.id(), "RefundPayment", 1,
                    paymentCommandsTopic, order.id().toString(), order.id(), null,
                    new RefundPayment(order.id(), "Cancellation requested by customer"));
        }
        return saved;
    }

    public record RefundPayment(UUID orderId, String reason) {}
}

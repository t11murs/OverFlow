package com.orderflow.order.application;

import com.orderflow.order.application.order.OrderApplicationService;
import com.orderflow.order.application.order.OrderApplicationService.CreateOrderCommand;
import com.orderflow.order.application.order.OrderApplicationService.OrderItemCommand;
import com.orderflow.order.application.order.OrderCreationTransaction;
import com.orderflow.order.application.order.OrderCancellationCoordinator;
import com.orderflow.order.application.port.CatalogOfferClient;
import com.orderflow.order.application.port.CatalogOfferSnapshot;
import com.orderflow.order.application.port.OrderRepository;
import com.orderflow.order.application.port.OrderProcessPort;
import com.orderflow.order.application.port.OutboxMessagePort;
import com.orderflow.order.config.MarketplaceProperties;
import com.orderflow.order.domain.model.CustomerOrder;
import com.orderflow.order.domain.model.OrderLineSnapshot;
import com.orderflow.order.domain.model.OrderStatus;
import com.orderflow.order.domain.shared.Money;
import com.orderflow.order.exception.OrderConflictException;
import com.orderflow.order.exception.OrderNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderProcessPort orderProcess;
    @Mock
    private CatalogOfferClient catalogOfferClient;
    @Mock
    private OutboxMessagePort outbox;
    @Mock
    private OrderCancellationCoordinator cancellationCoordinator;

    private OrderApplicationService service;

    @BeforeEach
    void setUp() {
        service = new OrderApplicationService(
                orderRepository,
                new OrderCreationTransaction(
                        orderRepository, orderProcess, outbox, "orderflow.inventory.commands.v1"),
                cancellationCoordinator,
                catalogOfferClient,
                new MarketplaceProperties(new BigDecimal("0.100000")),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsOrderFromTrustedCatalogSnapshots() {
        UUID customerId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        CreateOrderCommand command = new CreateOrderCommand(
                customerId, "checkout-1", List.of(new OrderItemCommand(offerId, 2)));
        when(orderRepository.findByCustomerIdAndIdempotencyKey(customerId, "checkout-1"))
                .thenReturn(Optional.empty());
        when(catalogOfferClient.resolveActiveOffers(List.of(offerId))).thenReturn(List.of(
                new CatalogOfferSnapshot(
                        offerId, UUID.randomUUID(), sellerId, "Phone", "SKU-1",
                        new BigDecimal("125.0000"), "RUB")));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerOrder order = service.create(command);

        assertEquals(new BigDecimal("250.0000"), order.total().amount());
        assertEquals("Phone", order.sellerOrders().getFirst().items().getFirst().productName());
        assertEquals(OrderStatus.RESERVING_INVENTORY, order.status());
        verify(outbox).append(
                org.mockito.ArgumentMatchers.eq("CustomerOrder"),
                org.mockito.ArgumentMatchers.eq(order.id()),
                org.mockito.ArgumentMatchers.eq("ReserveInventory"),
                org.mockito.ArgumentMatchers.eq(1),
                org.mockito.ArgumentMatchers.eq("orderflow.inventory.commands.v1"),
                org.mockito.ArgumentMatchers.eq(order.id().toString()),
                org.mockito.ArgumentMatchers.eq(order.id()),
                org.mockito.ArgumentMatchers.isNull(),
                any());
    }

    @Test
    void returnsExistingOrderForTheSameIdempotentRequest() {
        UUID customerId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        CreateOrderCommand command = new CreateOrderCommand(
                customerId, "checkout-1", List.of(new OrderItemCommand(offerId, 1)));

        when(orderRepository.findByCustomerIdAndIdempotencyKey(customerId, "checkout-1"))
                .thenReturn(Optional.empty());
        when(catalogOfferClient.resolveActiveOffers(List.of(offerId))).thenReturn(List.of(
                new CatalogOfferSnapshot(
                        offerId, UUID.randomUUID(), UUID.randomUUID(), "Phone", "SKU-1",
                        new BigDecimal("100.0000"), "RUB")));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CustomerOrder first = service.create(command);

        when(orderRepository.findByCustomerIdAndIdempotencyKey(customerId, "checkout-1"))
                .thenReturn(Optional.of(first));
        CustomerOrder replay = service.create(command);

        assertSame(first, replay);
        verify(catalogOfferClient, org.mockito.Mockito.times(1)).resolveActiveOffers(List.of(offerId));
    }

    @Test
    void rejectsReusedIdempotencyKeyForAnotherPayload() {
        UUID customerId = UUID.randomUUID();
        UUID oldOfferId = UUID.randomUUID();
        UUID newOfferId = UUID.randomUUID();
        CreateOrderCommand original = new CreateOrderCommand(
                customerId, "checkout-1", List.of(new OrderItemCommand(oldOfferId, 1)));
        when(orderRepository.findByCustomerIdAndIdempotencyKey(customerId, "checkout-1"))
                .thenReturn(Optional.empty());
        when(catalogOfferClient.resolveActiveOffers(List.of(oldOfferId))).thenReturn(List.of(
                new CatalogOfferSnapshot(
                        oldOfferId, UUID.randomUUID(), UUID.randomUUID(), "Phone", "SKU-1",
                        new BigDecimal("100.0000"), "RUB")));
        when(orderRepository.save(any(CustomerOrder.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CustomerOrder existing = service.create(original);

        when(orderRepository.findByCustomerIdAndIdempotencyKey(customerId, "checkout-1"))
                .thenReturn(Optional.of(existing));
        CreateOrderCommand changed = new CreateOrderCommand(
                customerId, "checkout-1", List.of(new OrderItemCommand(newOfferId, 1)));

        assertThrows(OrderConflictException.class, () -> service.create(changed));
        verify(catalogOfferClient, never()).resolveActiveOffers(List.of(newOfferId));
    }

    @Test
    void hidesOrderOwnedByAnotherCustomer() {
        UUID ownerId = UUID.randomUUID();
        CustomerOrder order = CustomerOrder.create(
                UUID.randomUUID(),
                ownerId,
                "checkout-1",
                "0".repeat(64),
                List.of(new OrderLineSnapshot(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Phone", "SKU-1",
                        new Money(new BigDecimal("100.0000"), "RUB"), 1)),
                new BigDecimal("0.100000"),
                NOW);
        when(orderRepository.findById(order.id())).thenReturn(Optional.of(order));

        assertThrows(OrderNotFoundException.class, () -> service.get(order.id(), UUID.randomUUID()));
    }
}


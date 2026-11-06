package com.orderflow.order.domain;

import com.orderflow.order.domain.model.CustomerOrder;
import com.orderflow.order.domain.model.OrderLineSnapshot;
import com.orderflow.order.domain.model.OrderStatus;
import com.orderflow.order.domain.model.SellerOrderStatus;
import com.orderflow.order.domain.shared.DomainRuleViolation;
import com.orderflow.order.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CustomerOrderTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String FINGERPRINT = "a".repeat(64);

    @Test
    void groupsLinesBySellerAndCalculatesCommissionSnapshots() {
        UUID firstSeller = UUID.randomUUID();
        UUID secondSeller = UUID.randomUUID();

        CustomerOrder order = CustomerOrder.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "checkout-1",
                FINGERPRINT,
                List.of(
                        line(firstSeller, "100.0000", 2),
                        line(firstSeller, "50.0000", 1),
                        line(secondSeller, "25.0000", 2)),
                new BigDecimal("0.100000"),
                NOW);

        assertEquals(new BigDecimal("300.0000"), order.total().amount());
        assertEquals(2, order.sellerOrders().size());
        assertEquals(new BigDecimal("25.0000"), order.sellerOrders().getFirst().commissionAmount().amount());
        assertEquals(new BigDecimal("225.0000"), order.sellerOrders().getFirst().netAmount().amount());
    }

    @Test
    void followsReservationPaymentAndConfirmationStateMachine() {
        CustomerOrder order = createSingleLineOrder();

        order.startInventoryReservation(NOW.plusSeconds(1));
        order.markInventoryReserved(NOW.plusSeconds(2));
        order.confirm(NOW.plusSeconds(3));

        assertEquals(OrderStatus.CONFIRMED, order.status());
        assertEquals(SellerOrderStatus.CONFIRMED, order.sellerOrders().getFirst().status());
    }

    @Test
    void rejectsMixedCurrencies() {
        UUID sellerId = UUID.randomUUID();
        List<OrderLineSnapshot> lines = List.of(
                line(sellerId, "10.0000", 1),
                new OrderLineSnapshot(
                        UUID.randomUUID(), UUID.randomUUID(), sellerId, "Second", "SKU-2",
                        new Money(new BigDecimal("10.0000"), "USD"), 1));

        assertThrows(DomainRuleViolation.class, () -> CustomerOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), "checkout-2", FINGERPRINT,
                lines, new BigDecimal("0.100000"), NOW));
    }

    @Test
    void newlyCreatedOrderCanBeCancelledWithoutSaga() {
        CustomerOrder order = createSingleLineOrder();

        order.requestCancellation(NOW.plusSeconds(1));

        assertEquals(OrderStatus.CANCELLED, order.status());
        assertEquals(SellerOrderStatus.CANCELLED, order.sellerOrders().getFirst().status());
    }

    @Test
    void exposesFailedCompensationInsteadOfCompletedCancellation() {
        CustomerOrder order = createSingleLineOrder();
        order.startInventoryReservation(NOW.plusSeconds(1));
        order.markInventoryReserved(NOW.plusSeconds(2));
        order.requestCancellation(NOW.plusSeconds(3));

        order.markCompensationFailed(NOW.plusSeconds(4));

        assertEquals(OrderStatus.COMPENSATION_FAILED, order.status());
        assertEquals(SellerOrderStatus.COMPENSATION_FAILED, order.sellerOrders().getFirst().status());
    }

    private static CustomerOrder createSingleLineOrder() {
        return CustomerOrder.create(
                UUID.randomUUID(), UUID.randomUUID(), "checkout-1", FINGERPRINT,
                List.of(line(UUID.randomUUID(), "100.0000", 1)),
                new BigDecimal("0.100000"), NOW);
    }

    private static OrderLineSnapshot line(UUID sellerId, String price, long quantity) {
        return new OrderLineSnapshot(
                UUID.randomUUID(),
                UUID.randomUUID(),
                sellerId,
                "Product",
                "SKU-" + UUID.randomUUID(),
                new Money(new BigDecimal(price), "RUB"),
                quantity);
    }
}


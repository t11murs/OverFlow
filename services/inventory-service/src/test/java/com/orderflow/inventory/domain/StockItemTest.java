package com.orderflow.inventory.domain;

import com.orderflow.inventory.domain.model.StockItem;
import com.orderflow.inventory.domain.shared.DomainRuleViolation;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockItemTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void reserveReleaseCommitAndRestorePreserveQuantities() {
        StockItem stock = stock(10);

        stock.reserve(6, NOW.plusSeconds(1));
        assertEquals(4, stock.available());
        stock.release(2, NOW.plusSeconds(2));
        stock.commit(4, NOW.plusSeconds(3));
        assertEquals(6, stock.onHand());
        assertEquals(0, stock.reserved());
        stock.restore(4, NOW.plusSeconds(4));

        assertEquals(10, stock.onHand());
        assertEquals(10, stock.available());
    }

    @Test
    void cannotReserveMoreThanAvailable() {
        StockItem stock = stock(5);

        assertThrows(DomainRuleViolation.class, () -> stock.reserve(6, NOW));
    }

    @Test
    void adjustmentCannotDropBelowReservedQuantity() {
        StockItem stock = stock(10);
        stock.reserve(8, NOW);

        assertThrows(DomainRuleViolation.class, () -> stock.adjustOnHand(-3, NOW));
        assertEquals(10, stock.onHand());
        assertEquals(8, stock.reserved());
    }

    private static StockItem stock(long onHand) {
        return StockItem.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), onHand, NOW);
    }
}


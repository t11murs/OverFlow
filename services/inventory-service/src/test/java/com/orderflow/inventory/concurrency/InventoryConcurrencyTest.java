package com.orderflow.inventory.concurrency;

import com.orderflow.inventory.application.inventory.ReservationService;
import com.orderflow.inventory.application.inventory.ReservationService.CreateReservationCommand;
import com.orderflow.inventory.application.inventory.ReservationService.ReservationLine;
import com.orderflow.inventory.application.inventory.StockService;
import com.orderflow.inventory.application.inventory.StockService.AdjustStockCommand;
import com.orderflow.inventory.application.inventory.WarehouseService;
import com.orderflow.inventory.application.inventory.WarehouseService.CreateWarehouseCommand;
import com.orderflow.inventory.application.port.CatalogOfferOwnershipClient;
import com.orderflow.inventory.exception.InventoryConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class InventoryConcurrencyTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withStartupTimeout(java.time.Duration.ofMinutes(3));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("inventory.expiry-scan-delay", () -> "1h");
        registry.add("messaging.outbox.poll-delay", () -> "1h");
        registry.add("management.queue-metrics.initial-delay", () -> "1h");
    }

    @MockitoBean private CatalogOfferOwnershipClient catalogOffers;
    @Autowired private WarehouseService warehouseService;
    @Autowired private StockService stockService;
    @Autowired private ReservationService reservationService;

    @Test
    void realReservationFlowPreventsOverselling() throws Exception {
        UUID sellerId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID warehouseId = warehouseService.create(
                new CreateWarehouseCommand(sellerId, "Concurrency warehouse", 1)).id();
        stockService.adjust(new AdjustStockCommand(
                warehouseId, offerId, sellerId, 10, "initial-stock-" + offerId));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> first = executor.submit(() -> reserveEight(sellerId, offerId, ready, start));
            Future<Boolean> second = executor.submit(() -> reserveEight(sellerId, offerId, ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int successful = (first.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (second.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successful);
        }

        StockService.Availability availability = stockService.getAvailability(offerId);
        assertEquals(10, availability.onHand());
        assertEquals(8, availability.reserved());
        assertEquals(2, availability.available());
    }

    @Test
    void repeatedStockAdjustmentWithSameKeyIsAppliedOnce() {
        UUID sellerId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID warehouseId = warehouseService.create(
                new CreateWarehouseCommand(sellerId, "Idempotency warehouse", 1)).id();
        AdjustStockCommand command = new AdjustStockCommand(
                warehouseId, offerId, sellerId, 7, "stock-request-1");

        stockService.adjust(command);
        stockService.adjust(command);

        assertEquals(7, stockService.getAvailability(offerId).onHand());
        org.junit.jupiter.api.Assertions.assertThrows(InventoryConflictException.class,
                () -> stockService.adjust(new AdjustStockCommand(
                        warehouseId, offerId, sellerId, 3, "stock-request-1")));
    }

    private boolean reserveEight(
            UUID sellerId,
            UUID offerId,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            return false;
        }
        try {
            reservationService.reserve(new CreateReservationCommand(
                    UUID.randomUUID(),
                    List.of(new ReservationLine(UUID.randomUUID(), offerId, sellerId, 8))));
            return true;
        } catch (InventoryConflictException exception) {
            return false;
        }
    }
}

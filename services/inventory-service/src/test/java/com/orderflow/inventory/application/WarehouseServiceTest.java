package com.orderflow.inventory.application;

import com.orderflow.inventory.application.inventory.WarehouseService;
import com.orderflow.inventory.application.port.WarehouseRepository;
import com.orderflow.inventory.domain.model.Warehouse;
import com.orderflow.inventory.exception.InventoryNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WarehouseServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private WarehouseRepository repository;

    private WarehouseService service;

    @BeforeEach
    void setUp() {
        service = new WarehouseService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void hidesWarehouseOwnedByAnotherSeller() {
        Warehouse warehouse = Warehouse.create(
                UUID.randomUUID(), UUID.randomUUID(), "Основной склад", 10, NOW);
        when(repository.findById(warehouse.id())).thenReturn(Optional.of(warehouse));

        assertThrows(InventoryNotFoundException.class,
                () -> service.get(warehouse.id(), UUID.randomUUID()));
    }
}

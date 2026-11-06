package com.orderflow.inventory.application;

import com.orderflow.inventory.application.inventory.ReservationService;
import com.orderflow.inventory.application.inventory.ReservationService.CreateReservationCommand;
import com.orderflow.inventory.application.inventory.ReservationService.ReservationLine;
import com.orderflow.inventory.application.port.ReservationRepository;
import com.orderflow.inventory.application.port.StockRepository;
import com.orderflow.inventory.config.InventoryProperties;
import com.orderflow.inventory.domain.model.StockItem;
import com.orderflow.inventory.domain.model.StockReservation;
import com.orderflow.inventory.exception.InventoryConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock private ReservationRepository reservationRepository;
    @Mock private StockRepository stockRepository;
    private ReservationService service;

    @BeforeEach
    void setUp() {
        service = new ReservationService(
                reservationRepository,
                stockRepository,
                new InventoryProperties(Duration.ofMinutes(15), 100),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void splitsLineAcrossWarehousesWhenNoSingleWarehouseIsSufficient() {
        UUID orderId = UUID.randomUUID();
        UUID orderItemId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        StockItem first = stock(offerId, sellerId, 3);
        StockItem second = stock(offerId, sellerId, 5);
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(stockRepository.findAllocationCandidatesForUpdate(offerId, sellerId, 6))
                .thenReturn(List.of(first, second));
        when(stockRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        StockReservation reservation = service.reserve(new CreateReservationCommand(
                orderId, List.of(new ReservationLine(orderItemId, offerId, sellerId, 6))));

        assertEquals(2, reservation.items().size());
        assertEquals(3, first.reserved());
        assertEquals(3, second.reserved());
        assertEquals(NOW.plus(Duration.ofMinutes(15)), reservation.expiresAt());
    }

    @Test
    void insufficientStockDoesNotPersistPartialAllocation() {
        UUID orderId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(stockRepository.findAllocationCandidatesForUpdate(offerId, sellerId, 10))
                .thenReturn(List.of(stock(offerId, sellerId, 4)));

        assertThrows(InventoryConflictException.class, () -> service.reserve(new CreateReservationCommand(
                orderId,
                List.of(new ReservationLine(UUID.randomUUID(), offerId, sellerId, 10)))));

        verify(stockRepository, never()).saveAll(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void identicalReservationRequestIsIdempotent() {
        UUID orderId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        CreateReservationCommand command = new CreateReservationCommand(
                orderId, List.of(new ReservationLine(UUID.randomUUID(), offerId, sellerId, 2)));
        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.empty());
        when(stockRepository.findAllocationCandidatesForUpdate(offerId, sellerId, 2))
                .thenReturn(List.of(stock(offerId, sellerId, 5)));
        when(stockRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        StockReservation first = service.reserve(command);

        when(reservationRepository.findByOrderId(orderId)).thenReturn(Optional.of(first));
        StockReservation replay = service.reserve(command);

        assertEquals(first.id(), replay.id());
        verify(stockRepository, org.mockito.Mockito.times(1))
                .findAllocationCandidatesForUpdate(offerId, sellerId, 2);
    }

    private static StockItem stock(UUID offerId, UUID sellerId, long onHand) {
        return StockItem.create(
                UUID.randomUUID(), UUID.randomUUID(), offerId, sellerId, onHand, NOW);
    }
}


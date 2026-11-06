package com.orderflow.inventory.application.inventory;

import com.orderflow.inventory.application.port.ReservationRepository;
import com.orderflow.inventory.application.port.StockRepository;
import com.orderflow.inventory.config.InventoryProperties;
import com.orderflow.inventory.domain.model.ReservationItem;
import com.orderflow.inventory.domain.model.ReservationStatus;
import com.orderflow.inventory.domain.model.StockItem;
import com.orderflow.inventory.domain.model.StockReservation;
import com.orderflow.inventory.domain.shared.DomainRuleViolation;
import com.orderflow.inventory.exception.InventoryConflictException;
import com.orderflow.inventory.exception.InventoryNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final StockRepository stockRepository;
    private final InventoryProperties properties;
    private final Clock clock;

    public ReservationService(
            ReservationRepository reservationRepository,
            StockRepository stockRepository,
            InventoryProperties properties,
            Clock clock
    ) {
        this.reservationRepository = reservationRepository;
        this.stockRepository = stockRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public StockReservation reserve(CreateReservationCommand command) {
        validateCommand(command);
        String fingerprint = fingerprint(command);
        reservationRepository.lockOrderId(command.orderId());
        StockReservation existing = reservationRepository.findByOrderId(command.orderId()).orElse(null);
        if (existing != null) {
            if (!existing.requestFingerprint().equals(fingerprint)) {
                throw new InventoryConflictException(
                        "RESERVATION_REQUEST_CHANGED", "Order already has a reservation with another payload");
            }
            return existing;
        }

        Instant now = clock.instant();
        Map<UUID, StockItem> modifiedStock = new LinkedHashMap<>();
        List<ReservationItem> allocations = new ArrayList<>();
        List<ReservationLine> orderedLines = command.lines().stream()
                .sorted(Comparator.comparing((ReservationLine line) -> line.offerId().toString())
                        .thenComparing(line -> line.orderItemId().toString()))
                .toList();

        for (ReservationLine line : orderedLines) {
            stockRepository.lockActiveWarehousesForAllocation(line.offerId(), line.sellerId());
            List<StockItem> candidates = stockRepository.findAllocationCandidatesForUpdate(
                    line.offerId(), line.sellerId(), line.quantity());
            long remaining = line.quantity();
            for (StockItem stock : candidates) {
                long allocated = Math.min(stock.available(), remaining);
                if (allocated <= 0) {
                    continue;
                }
                stock.reserve(allocated, now);
                modifiedStock.put(stock.id(), stock);
                allocations.add(new ReservationItem(
                        UUID.randomUUID(),
                        line.orderItemId(),
                        line.offerId(),
                        line.sellerId(),
                        stock.warehouseId(),
                        allocated,
                        now));
                remaining -= allocated;
                if (remaining == 0) {
                    break;
                }
            }
            if (remaining > 0) {
                throw new InventoryConflictException(
                        "INSUFFICIENT_STOCK", "Not enough stock for offer " + line.offerId());
            }
        }

        stockRepository.saveAll(List.copyOf(modifiedStock.values()));
        StockReservation reservation = StockReservation.create(
                UUID.randomUUID(),
                command.orderId(),
                fingerprint,
                now.plus(properties.reservationTtl()),
                allocations,
                now);
        return reservationRepository.save(reservation);
    }

    @Transactional
    public StockReservation confirm(UUID orderId) {
        StockReservation reservation = getForUpdate(orderId);
        if (reservation.status() == ReservationStatus.CONFIRMED) {
            return reservation;
        }
        Instant now = clock.instant();
        reservation.confirm(now);
        mutateAllocatedStock(reservation, now, StockMutation.COMMIT);
        return reservationRepository.save(reservation);
    }

    @Transactional
    public StockReservation release(UUID orderId) {
        StockReservation reservation = getForUpdate(orderId);
        if (reservation.status() == ReservationStatus.RELEASED || reservation.status() == ReservationStatus.EXPIRED) {
            return reservation;
        }
        Instant now = clock.instant();
        StockMutation mutation = reservation.status() == ReservationStatus.CONFIRMED
                ? StockMutation.RESTORE
                : StockMutation.RELEASE;
        mutateAllocatedStock(reservation, now, mutation);
        reservation.release(now);
        return reservationRepository.save(reservation);
    }

    @Transactional
    public int expireBatch() {
        Instant now = clock.instant();
        List<StockReservation> reservations = reservationRepository.findExpiredActiveForUpdate(
                now, properties.expiryBatchSize());
        for (StockReservation reservation : reservations) {
            mutateAllocatedStock(reservation, now, StockMutation.RELEASE);
            reservation.expire(now);
            reservationRepository.save(reservation);
        }
        return reservations.size();
    }

    @Transactional(readOnly = true)
    public StockReservation get(UUID orderId) {
        return reservationRepository.findByOrderId(orderId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "RESERVATION_NOT_FOUND", "Reservation was not found"));
    }

    private StockReservation getForUpdate(UUID orderId) {
        return reservationRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() -> new InventoryNotFoundException(
                        "RESERVATION_NOT_FOUND", "Reservation was not found"));
    }

    private void mutateAllocatedStock(StockReservation reservation, Instant now, StockMutation mutation) {
        List<ReservationItem> orderedItems = reservation.items().stream()
                .sorted(Comparator.comparing((ReservationItem item) -> item.offerId().toString())
                        .thenComparing(item -> item.warehouseId().toString()))
                .toList();
        List<StockItem> changed = new ArrayList<>();
        for (ReservationItem item : orderedItems) {
            StockItem stock = stockRepository
                    .findByOfferAndWarehouseForUpdate(item.offerId(), item.warehouseId())
                    .orElseThrow(() -> new InventoryNotFoundException(
                            "STOCK_NOT_FOUND", "Allocated stock was not found"));
            try {
                switch (mutation) {
                    case RELEASE -> stock.release(item.quantity(), now);
                    case COMMIT -> stock.commit(item.quantity(), now);
                    case RESTORE -> stock.restore(item.quantity(), now);
                }
            } catch (ArithmeticException exception) {
                throw new InventoryConflictException("STOCK_OVERFLOW", "Stock quantity exceeds supported range");
            }
            changed.add(stock);
        }
        stockRepository.saveAll(changed);
    }

    private static void validateCommand(CreateReservationCommand command) {
        if (command == null || command.orderId() == null || command.lines() == null
                || command.lines().isEmpty() || command.lines().size() > 100) {
            throw new DomainRuleViolation("INVALID_RESERVATION_REQUEST", "Reservation request is invalid");
        }
        if (command.lines().stream().anyMatch(Objects::isNull)) {
            throw new DomainRuleViolation("INVALID_RESERVATION_LINE", "Reservation line is invalid");
        }
        if (command.lines().stream().map(ReservationLine::orderItemId).distinct().count() != command.lines().size()) {
            throw new DomainRuleViolation("DUPLICATE_ORDER_ITEM", "Reservation lines must have unique order item IDs");
        }
        if (command.lines().stream().map(ReservationLine::offerId).distinct().count() != command.lines().size()) {
            throw new DomainRuleViolation("DUPLICATE_OFFER_ID", "Reservation lines must have unique offer IDs");
        }
        command.lines().forEach(line -> {
            if (line.orderItemId() == null || line.offerId() == null || line.sellerId() == null
                    || line.quantity() < 1 || line.quantity() > 10_000) {
                throw new DomainRuleViolation("INVALID_RESERVATION_LINE", "Reservation line is invalid");
            }
        });
    }

    private static String fingerprint(CreateReservationCommand command) {
        String value = command.orderId() + "|" + command.lines().stream()
                .sorted(Comparator.comparing(line -> line.orderItemId().toString()))
                .map(line -> line.orderItemId() + ":" + line.offerId() + ":" + line.sellerId() + ":" + line.quantity())
                .collect(Collectors.joining("|"));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private enum StockMutation { RELEASE, COMMIT, RESTORE }

    public record CreateReservationCommand(UUID orderId, List<ReservationLine> lines) {}
    public record ReservationLine(UUID orderItemId, UUID offerId, UUID sellerId, long quantity) {}
}


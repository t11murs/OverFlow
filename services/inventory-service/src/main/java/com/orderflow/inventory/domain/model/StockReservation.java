package com.orderflow.inventory.domain.model;

import com.orderflow.inventory.domain.shared.DomainRuleViolation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class StockReservation {

    private final UUID id;
    private final UUID orderId;
    private final String requestFingerprint;
    private ReservationStatus status;
    private final Instant expiresAt;
    private final List<ReservationItem> items;
    private final long version;
    private final Instant createdAt;
    private Instant updatedAt;

    public StockReservation(
            UUID id,
            UUID orderId,
            String requestFingerprint,
            ReservationStatus status,
            Instant expiresAt,
            List<ReservationItem> items,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.orderId = Objects.requireNonNull(orderId, "orderId must not be null");
        if (requestFingerprint == null || !requestFingerprint.matches("[0-9a-f]{64}")) {
            throw new DomainRuleViolation("INVALID_REQUEST_FINGERPRINT", "Reservation fingerprint is invalid");
        }
        this.requestFingerprint = requestFingerprint;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        this.items = List.copyOf(items);
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        if (this.items.isEmpty()) {
            throw new DomainRuleViolation("INVALID_RESERVATION", "Reservation must contain allocations");
        }
    }

    public static StockReservation create(
            UUID id,
            UUID orderId,
            String fingerprint,
            Instant expiresAt,
            List<ReservationItem> items,
            Instant now
    ) {
        if (!expiresAt.isAfter(now)) {
            throw new DomainRuleViolation("INVALID_RESERVATION_EXPIRY", "Reservation expiry must be in the future");
        }
        return new StockReservation(
                id, orderId, fingerprint, ReservationStatus.ACTIVE, expiresAt, items, 0, now, now);
    }

    public void confirm(Instant now) {
        transitionFromActive(ReservationStatus.CONFIRMED, now);
    }

    public void release(Instant now) {
        if (status == ReservationStatus.RELEASED) {
            return;
        }
        if (status == ReservationStatus.EXPIRED) {
            throw new DomainRuleViolation("RESERVATION_EXPIRED", "Expired reservation cannot be released again");
        }
        status = ReservationStatus.RELEASED;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void expire(Instant now) {
        if (status != ReservationStatus.ACTIVE || expiresAt.isAfter(now)) {
            throw new DomainRuleViolation("INVALID_RESERVATION_EXPIRY", "Reservation cannot be expired");
        }
        status = ReservationStatus.EXPIRED;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    private void transitionFromActive(ReservationStatus target, Instant now) {
        if (status == target) {
            return;
        }
        if (status != ReservationStatus.ACTIVE) {
            throw new DomainRuleViolation(
                    "INVALID_RESERVATION_TRANSITION", "Cannot change reservation from " + status + " to " + target);
        }
        if (expiresAt.isBefore(now) || expiresAt.equals(now)) {
            throw new DomainRuleViolation("RESERVATION_EXPIRED", "Reservation has expired");
        }
        status = target;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public UUID id() { return id; }
    public UUID orderId() { return orderId; }
    public String requestFingerprint() { return requestFingerprint; }
    public ReservationStatus status() { return status; }
    public Instant expiresAt() { return expiresAt; }
    public List<ReservationItem> items() { return items; }
    public long version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}


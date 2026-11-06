package com.orderflow.identity.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class UserAccount {

    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final UserStatus status;
    private final Set<UserRole> roles;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;

    public UserAccount(UUID id, String email, String passwordHash, UserStatus status, Set<UserRole> roles,
                       Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id);
        this.email = normalizeEmail(email);
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.status = Objects.requireNonNull(status);
        this.roles = new java.util.HashSet<>(roles);
        if (this.roles.isEmpty()) {
            throw new IllegalArgumentException("roles must not be empty");
        }
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = Objects.requireNonNull(updatedAt);
        this.version = version;
    }

    public static UserAccount registerCustomer(UUID id, String email, String passwordHash, Instant now) {
        return new UserAccount(id, email, passwordHash, UserStatus.ACTIVE, Set.of(UserRole.CUSTOMER), now, now, 0);
    }

    public boolean canAuthenticate() {
        return status == UserStatus.ACTIVE;
    }

    public void addRole(UserRole role, Instant now) {
        roles.add(Objects.requireNonNull(role));
        this.updatedAt = Objects.requireNonNull(now);
    }

    public static String normalizeEmail(String value) {
        String normalized = requireText(value, "email").trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 320 || !normalized.contains("@")) {
            throw new IllegalArgumentException("email is invalid");
        }
        return normalized;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public UUID id() { return id; }
    public String email() { return email; }
    public String passwordHash() { return passwordHash; }
    public UserStatus status() { return status; }
    public Set<UserRole> roles() { return Set.copyOf(roles); }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
    public long version() { return version; }
}

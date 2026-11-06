package com.orderflow.identity.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class RefreshToken {

    private final UUID id;
    private final UUID userId;
    private final UUID familyId;
    private final String tokenHash;
    private final Instant expiresAt;
    private final Instant createdAt;
    private Instant revokedAt;
    private UUID replacedByTokenId;

    public RefreshToken(UUID id, UUID userId, UUID familyId, String tokenHash, Instant expiresAt, Instant revokedAt,
                        UUID replacedByTokenId, Instant createdAt) {
        this.id = Objects.requireNonNull(id);
        this.userId = Objects.requireNonNull(userId);
        this.familyId = Objects.requireNonNull(familyId);
        this.tokenHash = Objects.requireNonNull(tokenHash);
        this.expiresAt = Objects.requireNonNull(expiresAt);
        this.revokedAt = revokedAt;
        this.replacedByTokenId = replacedByTokenId;
        this.createdAt = Objects.requireNonNull(createdAt);
    }

    public boolean isUsableAt(Instant now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public void revoke(Instant now, UUID replacementId) {
        if (revokedAt == null) {
            revokedAt = Objects.requireNonNull(now);
            replacedByTokenId = replacementId;
        }
    }

    public UUID id() { return id; }
    public UUID userId() { return userId; }
    public UUID familyId() { return familyId; }
    public String tokenHash() { return tokenHash; }
    public Instant expiresAt() { return expiresAt; }
    public Instant revokedAt() { return revokedAt; }
    public UUID replacedByTokenId() { return replacedByTokenId; }
    public Instant createdAt() { return createdAt; }
}

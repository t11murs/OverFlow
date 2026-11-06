package com.orderflow.identity.adapter.outbound.persistence;

import com.orderflow.identity.domain.RefreshToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token")
class RefreshTokenEntity {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "replaced_by_token_id")
    private UUID replacedByTokenId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RefreshTokenEntity() {}

    RefreshTokenEntity(RefreshToken token) {
        id = token.id();
        updateFrom(token);
    }

    void updateFrom(RefreshToken token) {
        userId = token.userId();
        familyId = token.familyId();
        tokenHash = token.tokenHash();
        expiresAt = token.expiresAt();
        revokedAt = token.revokedAt();
        replacedByTokenId = token.replacedByTokenId();
        createdAt = token.createdAt();
    }

    RefreshToken toDomain() {
        return new RefreshToken(id, userId, familyId, tokenHash, expiresAt, revokedAt, replacedByTokenId, createdAt);
    }
}

package com.orderflow.identity.application.port;

import com.orderflow.identity.domain.RefreshToken;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository {
    Optional<UUID> findFamilyIdByHash(String tokenHash);
    void lockFamily(UUID familyId);
    Optional<RefreshToken> findByHashForUpdate(String tokenHash);
    RefreshToken save(RefreshToken token);
    int revokeFamily(UUID familyId, Instant revokedAt);
}

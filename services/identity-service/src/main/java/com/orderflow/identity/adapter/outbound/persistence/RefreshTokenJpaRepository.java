package com.orderflow.identity.adapter.outbound.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface RefreshTokenJpaRepository extends JpaRepository<RefreshTokenEntity, UUID> {

    @Query("select token.familyId from RefreshTokenEntity token where token.tokenHash = :tokenHash")
    Optional<UUID> findFamilyIdByTokenHash(@Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select token from RefreshTokenEntity token where token.tokenHash = :tokenHash")
    Optional<RefreshTokenEntity> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update RefreshTokenEntity token set token.revokedAt = :revokedAt "
            + "where token.familyId = :familyId and token.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("revokedAt") Instant revokedAt);
}

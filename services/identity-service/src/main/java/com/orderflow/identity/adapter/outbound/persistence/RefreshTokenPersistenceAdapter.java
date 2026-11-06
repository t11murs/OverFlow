package com.orderflow.identity.adapter.outbound.persistence;

import com.orderflow.identity.application.port.RefreshTokenRepository;
import com.orderflow.identity.domain.RefreshToken;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
class RefreshTokenPersistenceAdapter implements RefreshTokenRepository {

    private final RefreshTokenJpaRepository repository;
    private final JdbcTemplate jdbc;

    RefreshTokenPersistenceAdapter(RefreshTokenJpaRepository repository, JdbcTemplate jdbc) {
        this.repository = repository;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UUID> findFamilyIdByHash(String tokenHash) {
        return repository.findFamilyIdByTokenHash(tokenHash);
    }

    @Override
    public void lockFamily(UUID familyId) {
        long lockKey = familyId.getMostSignificantBits() ^ familyId.getLeastSignificantBits();
        jdbc.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, lockKey);
                statement.execute();
            }
            return null;
        });
    }

    @Override
    public Optional<RefreshToken> findByHashForUpdate(String tokenHash) {
        return repository.findByTokenHashForUpdate(tokenHash).map(RefreshTokenEntity::toDomain);
    }

    @Override
    public RefreshToken save(RefreshToken token) {
        RefreshTokenEntity entity = repository.findById(token.id()).orElseGet(() -> new RefreshTokenEntity(token));
        entity.updateFrom(token);
        return repository.saveAndFlush(entity).toDomain();
    }

    @Override
    public int revokeFamily(UUID familyId, Instant revokedAt) {
        return repository.revokeFamily(familyId, revokedAt);
    }
}

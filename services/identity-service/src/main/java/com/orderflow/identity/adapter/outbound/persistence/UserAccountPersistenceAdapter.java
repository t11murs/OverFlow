package com.orderflow.identity.adapter.outbound.persistence;

import com.orderflow.identity.application.port.UserAccountRepository;
import com.orderflow.identity.domain.UserAccount;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
class UserAccountPersistenceAdapter implements UserAccountRepository {

    private final UserAccountJpaRepository repository;

    UserAccountPersistenceAdapter(UserAccountJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean existsByEmail(String email) {
        return repository.existsByEmailIgnoreCase(email);
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return repository.findByEmailIgnoreCase(email).map(UserAccountEntity::toDomain);
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        return repository.findById(id).map(UserAccountEntity::toDomain);
    }

    @Override
    public UserAccount save(UserAccount account) {
        UserAccountEntity entity = repository.findById(account.id())
                .orElseGet(() -> new UserAccountEntity(account));
        entity.updateFrom(account);
        return repository.saveAndFlush(entity).toDomain();
    }
}

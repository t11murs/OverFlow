package com.orderflow.identity.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface UserAccountJpaRepository extends JpaRepository<UserAccountEntity, UUID> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<UserAccountEntity> findByEmailIgnoreCase(String email);
}

package com.orderflow.identity.application.port;

import com.orderflow.identity.domain.UserAccount;

import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {
    boolean existsByEmail(String email);
    Optional<UserAccount> findByEmail(String email);
    Optional<UserAccount> findById(UUID id);
    UserAccount save(UserAccount account);
}

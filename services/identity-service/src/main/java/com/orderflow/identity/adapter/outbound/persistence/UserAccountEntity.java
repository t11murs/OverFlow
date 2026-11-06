package com.orderflow.identity.adapter.outbound.persistence;

import com.orderflow.identity.domain.UserAccount;
import com.orderflow.identity.domain.UserRole;
import com.orderflow.identity.domain.UserStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "user_account")
class UserAccountEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private Set<UserRole> roles = new HashSet<>();

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccountEntity() {}

    UserAccountEntity(UserAccount account) {
        id = account.id();
        createdAt = account.createdAt();
        updateFrom(account);
    }

    void updateFrom(UserAccount account) {
        email = account.email();
        passwordHash = account.passwordHash();
        status = account.status();
        roles.clear();
        roles.addAll(account.roles());
        updatedAt = account.updatedAt();
    }

    UserAccount toDomain() {
        return new UserAccount(id, email, passwordHash, status, roles, createdAt, updatedAt, version);
    }
}

package com.orderflow.identity.adapter.outbound.persistence;

import com.orderflow.identity.domain.SellerOrganization;
import com.orderflow.identity.domain.SellerStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seller_organization")
class SellerOrganizationEntity {

    @Id
    private UUID id;

    @Column(name = "legal_name", nullable = false, length = 255)
    private String legalName;

    @Column(name = "display_name", nullable = false, length = 160)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SellerStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SellerOrganizationEntity() {}

    SellerOrganizationEntity(SellerOrganization organization) {
        id = organization.id();
        legalName = organization.legalName();
        displayName = organization.displayName();
        status = organization.status();
        createdAt = organization.createdAt();
        updatedAt = organization.updatedAt();
    }

    SellerOrganization toDomain() {
        return new SellerOrganization(id, legalName, displayName, status, createdAt, updatedAt, version);
    }
}

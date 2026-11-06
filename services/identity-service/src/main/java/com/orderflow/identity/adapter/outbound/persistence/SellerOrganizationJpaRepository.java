package com.orderflow.identity.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SellerOrganizationJpaRepository extends JpaRepository<SellerOrganizationEntity, UUID> {}

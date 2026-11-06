package com.orderflow.catalog.adapter.outbound.persistence;

import com.orderflow.catalog.domain.offer.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

interface OfferJpaRepository extends JpaRepository<OfferEntity, UUID>, JpaSpecificationExecutor<OfferEntity> {

    boolean existsBySellerIdAndSellerSkuAndIdNot(UUID sellerId, String sellerSku, UUID id);

    boolean existsByProductIdAndStatus(UUID productId, OfferStatus status);
}


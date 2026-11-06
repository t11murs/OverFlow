package com.orderflow.catalog.application.port;

import com.orderflow.catalog.domain.offer.Offer;
import com.orderflow.catalog.domain.offer.OfferStatus;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OfferRepository {

    Optional<Offer> findById(UUID id);

    List<Offer> findAllById(Collection<UUID> ids);

    PageResult<Offer> search(UUID productId, UUID sellerId, OfferStatus status, int page, int size);

    boolean existsBySellerIdAndSellerSkuAndIdNot(UUID sellerId, String sellerSku, UUID excludedId);

    boolean existsByProductIdAndStatus(UUID productId, OfferStatus status);

    Offer save(Offer offer);
}


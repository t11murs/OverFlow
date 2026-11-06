package com.orderflow.catalog.adapter.outbound.persistence;

import com.orderflow.catalog.application.port.OfferRepository;
import com.orderflow.catalog.application.port.PageResult;
import com.orderflow.catalog.domain.offer.Offer;
import com.orderflow.catalog.domain.offer.OfferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
class OfferPersistenceAdapter implements OfferRepository {

    private final OfferJpaRepository repository;

    OfferPersistenceAdapter(OfferJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Offer> findById(UUID id) {
        return repository.findById(id).map(OfferEntity::toDomain);
    }

    @Override
    public List<Offer> findAllById(Collection<UUID> ids) {
        return repository.findAllById(ids).stream().map(OfferEntity::toDomain).toList();
    }

    @Override
    public PageResult<Offer> search(UUID productId, UUID sellerId, OfferStatus status, int page, int size) {
        Specification<OfferEntity> specification = Specification.allOf(
                equalSpecification("productId", productId),
                equalSpecification("sellerId", sellerId),
                equalSpecification("status", status));
        Page<OfferEntity> result = repository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id"))));
        return new PageResult<>(
                result.getContent().stream().map(OfferEntity::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public boolean existsBySellerIdAndSellerSkuAndIdNot(UUID sellerId, String sellerSku, UUID excludedId) {
        return repository.existsBySellerIdAndSellerSkuAndIdNot(sellerId, sellerSku, excludedId);
    }

    @Override
    public boolean existsByProductIdAndStatus(UUID productId, OfferStatus status) {
        return repository.existsByProductIdAndStatus(productId, status);
    }

    @Override
    public Offer save(Offer offer) {
        OfferEntity entity = repository.findById(offer.id()).orElseGet(() -> new OfferEntity(offer));
        entity.updateFrom(offer);
        return repository.saveAndFlush(entity).toDomain();
    }

    private static <T> Specification<OfferEntity> equalSpecification(String field, T value) {
        return value == null ? null : (root, criteriaQuery, builder) -> builder.equal(root.get(field), value);
    }
}


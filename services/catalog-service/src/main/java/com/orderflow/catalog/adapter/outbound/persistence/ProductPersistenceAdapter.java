package com.orderflow.catalog.adapter.outbound.persistence;

import com.orderflow.catalog.application.port.PageResult;
import com.orderflow.catalog.application.port.ProductRepository;
import com.orderflow.catalog.domain.product.Product;
import com.orderflow.catalog.domain.product.ProductStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ProductPersistenceAdapter implements ProductRepository {

    private final ProductJpaRepository repository;

    ProductPersistenceAdapter(ProductJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return repository.findById(id).map(ProductEntity::toDomain);
    }

    @Override
    public Optional<Product> findByIdForUpdate(UUID id) {
        return repository.findByIdForUpdate(id).map(ProductEntity::toDomain);
    }

    @Override
    public List<Product> findAllById(Collection<UUID> ids) {
        return repository.findAllById(ids).stream().map(ProductEntity::toDomain).toList();
    }

    @Override
    public PageResult<Product> search(String query, UUID categoryId, ProductStatus status, int page, int size) {
        Specification<ProductEntity> specification = Specification.allOf(
                querySpecification(query),
                equalSpecification("categoryId", categoryId),
                equalSpecification("status", status));
        Page<ProductEntity> result = repository.findAll(
                specification,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id"))));
        return new PageResult<>(
                result.getContent().stream().map(ProductEntity::toDomain).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Override
    public Product save(Product product) {
        ProductEntity entity = repository.findById(product.id()).orElseGet(() -> new ProductEntity(product));
        entity.updateFrom(product);
        return repository.saveAndFlush(entity).toDomain();
    }

    private static Specification<ProductEntity> querySpecification(String query) {
        if (query == null) {
            return null;
        }
        String escaped = query.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        String pattern = "%" + escaped + "%";
        return (root, criteriaQuery, builder) -> builder.or(
                builder.like(builder.lower(root.get("name")), pattern, '\\'),
                builder.like(builder.lower(root.get("brand")), pattern, '\\'));
    }

    private static <T> Specification<ProductEntity> equalSpecification(String field, T value) {
        return value == null ? null : (root, criteriaQuery, builder) -> builder.equal(root.get(field), value);
    }
}


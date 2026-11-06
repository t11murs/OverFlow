package com.orderflow.catalog.application.port;

import com.orderflow.catalog.domain.product.Product;
import com.orderflow.catalog.domain.product.ProductStatus;

import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductRepository {

    Optional<Product> findById(UUID id);

    Optional<Product> findByIdForUpdate(UUID id);

    List<Product> findAllById(Collection<UUID> ids);

    PageResult<Product> search(String query, UUID categoryId, ProductStatus status, int page, int size);

    Product save(Product product);
}


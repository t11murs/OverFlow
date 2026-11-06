package com.orderflow.catalog.adapter.outbound.persistence;

import com.orderflow.catalog.domain.product.Product;
import com.orderflow.catalog.domain.product.ProductStatus;
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
@Table(name = "product")
class ProductEntity {

    @Id
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 160)
    private String brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductStatus status;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProductEntity() {}

    ProductEntity(Product product) {
        id = product.id();
        createdAt = product.createdAt();
        updateFrom(product);
    }

    void updateFrom(Product product) {
        categoryId = product.categoryId();
        name = product.name();
        description = product.description();
        brand = product.brand();
        status = product.status();
        updatedAt = product.updatedAt();
    }

    Product toDomain() {
        return new Product(id, categoryId, name, description, brand, status, createdAt, updatedAt, version);
    }

    UUID id() { return id; }
}


package com.orderflow.catalog.adapter.outbound.persistence;

import com.orderflow.catalog.domain.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "category")
class CategoryEntity {

    @Id
    private UUID id;

    @Column(name = "parent_id")
    private UUID parentId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 180)
    private String slug;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CategoryEntity() {}

    CategoryEntity(Category category) {
        id = category.id();
        createdAt = category.createdAt();
        updateFrom(category);
    }

    void updateFrom(Category category) {
        parentId = category.parentId();
        name = category.name();
        slug = category.slug();
        updatedAt = category.updatedAt();
    }

    Category toDomain() {
        return new Category(id, parentId, name, slug, createdAt, updatedAt, version);
    }

    UUID id() { return id; }
}


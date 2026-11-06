package com.orderflow.catalog.adapter.outbound.persistence;

import com.orderflow.catalog.application.port.CategoryRepository;
import com.orderflow.catalog.domain.category.Category;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class CategoryPersistenceAdapter implements CategoryRepository {

    private final CategoryJpaRepository repository;

    CategoryPersistenceAdapter(CategoryJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void lockHierarchy() {
        repository.acquireHierarchyLock();
    }

    @Override
    public Optional<Category> findById(UUID id) {
        return repository.findById(id).map(CategoryEntity::toDomain);
    }

    @Override
    public List<Category> findAll() {
        return repository.findAllByOrderByNameAsc().stream().map(CategoryEntity::toDomain).toList();
    }

    @Override
    public boolean existsBySlugAndIdNot(String slug, UUID excludedId) {
        return repository.existsBySlugAndIdNot(slug, excludedId);
    }

    @Override
    public Category save(Category category) {
        CategoryEntity entity = repository.findById(category.id()).orElseGet(() -> new CategoryEntity(category));
        entity.updateFrom(category);
        return repository.saveAndFlush(entity).toDomain();
    }
}


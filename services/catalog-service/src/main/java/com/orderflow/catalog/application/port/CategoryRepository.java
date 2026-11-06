package com.orderflow.catalog.application.port;

import com.orderflow.catalog.domain.category.Category;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository {

    void lockHierarchy();

    Optional<Category> findById(UUID id);

    List<Category> findAll();

    boolean existsBySlugAndIdNot(String slug, UUID excludedId);

    Category save(Category category);
}


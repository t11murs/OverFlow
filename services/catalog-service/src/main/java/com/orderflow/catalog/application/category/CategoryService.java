package com.orderflow.catalog.application.category;

import com.orderflow.catalog.application.port.CategoryRepository;
import com.orderflow.catalog.domain.category.Category;
import com.orderflow.catalog.exception.CatalogConflictException;
import com.orderflow.catalog.exception.CatalogNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class CategoryService {

    private static final int MAX_CATEGORY_DEPTH = 32;

    private final CategoryRepository repository;
    private final Clock clock;

    public CategoryService(CategoryRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public Category create(CreateCategoryCommand command) {
        repository.lockHierarchy();
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        Category category = Category.create(id, command.parentId(), command.name(), command.slug(), now);
        assertSlugAvailable(category.slug(), id);
        assertValidParentChain(id, category.parentId());
        return repository.save(category);
    }

    @Transactional
    public Category update(UUID id, UpdateCategoryCommand command) {
        repository.lockHierarchy();
        Category category = getRequired(id);
        assertValidParentChain(id, command.parentId());
        category.changeDetails(command.parentId(), command.name(), command.slug(), clock.instant());
        assertSlugAvailable(category.slug(), id);
        return repository.save(category);
    }

    @Transactional(readOnly = true)
    public Category get(UUID id) {
        return getRequired(id);
    }

    @Transactional(readOnly = true)
    public List<Category> list() {
        return repository.findAll();
    }

    private Category getRequired(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new CatalogNotFoundException("CATEGORY_NOT_FOUND", "Category was not found"));
    }

    private void assertSlugAvailable(String slug, UUID excludedId) {
        if (repository.existsBySlugAndIdNot(slug, excludedId)) {
            throw new CatalogConflictException("DUPLICATE_CATEGORY_SLUG", "Category slug is already used");
        }
    }

    private void assertValidParentChain(UUID categoryId, UUID parentId) {
        if (parentId == null) {
            return;
        }
        Set<UUID> visited = new HashSet<>();
        UUID cursor = parentId;
        int depth = 0;
        while (cursor != null) {
            if (cursor.equals(categoryId) || !visited.add(cursor)) {
                throw new CatalogConflictException("CATEGORY_CYCLE", "Category parent would create a cycle");
            }
            if (++depth > MAX_CATEGORY_DEPTH) {
                throw new CatalogConflictException("CATEGORY_DEPTH_EXCEEDED", "Category tree depth exceeds " + MAX_CATEGORY_DEPTH);
            }
            Category parent = repository.findById(cursor)
                    .orElseThrow(() -> new CatalogNotFoundException("PARENT_CATEGORY_NOT_FOUND", "Parent category was not found"));
            cursor = parent.parentId();
        }
    }

    public record CreateCategoryCommand(UUID parentId, String name, String slug) {}

    public record UpdateCategoryCommand(UUID parentId, String name, String slug) {}
}


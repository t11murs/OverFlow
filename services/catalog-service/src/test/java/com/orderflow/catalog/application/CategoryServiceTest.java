package com.orderflow.catalog.application;

import com.orderflow.catalog.application.category.CategoryService;
import com.orderflow.catalog.application.category.CategoryService.UpdateCategoryCommand;
import com.orderflow.catalog.application.port.CategoryRepository;
import com.orderflow.catalog.domain.category.Category;
import com.orderflow.catalog.exception.CatalogConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private CategoryRepository repository;

    private CategoryService service;

    @BeforeEach
    void setUp() {
        service = new CategoryService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void rejectsCategoryCycleThroughAnAncestor() {
        UUID categoryId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        Category category = Category.create(categoryId, null, "Root", "root", NOW);
        Category parent = Category.create(parentId, categoryId, "Child", "child", NOW);
        when(repository.findById(categoryId)).thenReturn(Optional.of(category));
        when(repository.findById(parentId)).thenReturn(Optional.of(parent));

        assertThrows(CatalogConflictException.class,
                () -> service.update(categoryId, new UpdateCategoryCommand(parentId, "Root", "root")));
    }
}


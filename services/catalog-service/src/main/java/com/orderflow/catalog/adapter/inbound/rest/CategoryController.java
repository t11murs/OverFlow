package com.orderflow.catalog.adapter.inbound.rest;

import com.orderflow.catalog.api.CatalogDtos.CategoryRequest;
import com.orderflow.catalog.api.CatalogDtos.CategoryResponse;
import com.orderflow.catalog.application.category.CategoryService;
import com.orderflow.catalog.application.category.CategoryService.CreateCategoryCommand;
import com.orderflow.catalog.application.category.CategoryService.UpdateCategoryCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/categories")
@Tag(name = "Catalog categories")
public class CategoryController {

    private final CategoryService service;

    public CategoryController(CategoryService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a category")
    public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        CategoryResponse response = CatalogDtoMapper.toResponse(service.create(
                new CreateCategoryCommand(request.parentId(), request.name(), request.slug())));
        return ResponseEntity.created(URI.create("/api/v1/catalog/categories/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a category")
    public CategoryResponse update(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
        return CatalogDtoMapper.toResponse(service.update(
                id, new UpdateCategoryCommand(request.parentId(), request.name(), request.slug())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a category")
    public CategoryResponse get(@PathVariable UUID id) {
        return CatalogDtoMapper.toResponse(service.get(id));
    }

    @GetMapping
    @Operation(summary = "List categories")
    public List<CategoryResponse> list() {
        return service.list().stream().map(CatalogDtoMapper::toResponse).toList();
    }
}


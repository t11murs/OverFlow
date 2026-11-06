package com.orderflow.catalog.adapter.inbound.rest;

import com.orderflow.catalog.api.CatalogDtos.PageResponse;
import com.orderflow.catalog.api.CatalogDtos.ProductRequest;
import com.orderflow.catalog.api.CatalogDtos.ProductResponse;
import com.orderflow.catalog.api.CatalogDtos.ProductStatusRequest;
import com.orderflow.catalog.application.product.ProductService;
import com.orderflow.catalog.application.product.ProductService.CreateProductCommand;
import com.orderflow.catalog.application.product.ProductService.UpdateProductCommand;
import com.orderflow.catalog.config.CatalogJwtPrincipal;
import com.orderflow.catalog.config.CatalogCacheConfiguration;
import com.orderflow.catalog.domain.product.ProductStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/catalog/products")
@Tag(name = "Catalog products")
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a draft product")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse response = CatalogDtoMapper.toResponse(service.create(new CreateProductCommand(
                request.categoryId(), request.name(), request.description(), request.brand())));
        return ResponseEntity.created(URI.create("/api/v1/catalog/products/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @CacheEvict(cacheNames = CatalogCacheConfiguration.PUBLIC_PRODUCT_CACHE, key = "#id")
    @Operation(summary = "Update product details")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return CatalogDtoMapper.toResponse(service.update(id, new UpdateProductCommand(
                request.categoryId(), request.name(), request.description(), request.brand())));
    }

    @PatchMapping("/{id}/status")
    @CacheEvict(cacheNames = CatalogCacheConfiguration.PUBLIC_PRODUCT_CACHE, key = "#id")
    @Operation(summary = "Change product lifecycle status")
    public ProductResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody ProductStatusRequest request) {
        return CatalogDtoMapper.toResponse(service.changeStatus(id, request.status()));
    }

    @GetMapping("/{id}")
    @Cacheable(
            cacheNames = CatalogCacheConfiguration.PUBLIC_PRODUCT_CACHE,
            key = "#id",
            condition = "#principal == null"
    )
    @Operation(summary = "Get a product")
    public ProductResponse get(
            @AuthenticationPrincipal CatalogJwtPrincipal principal,
            @PathVariable UUID id
    ) {
        return CatalogDtoMapper.toResponse(service.getVisible(id, isAdministrator(principal)));
    }

    @GetMapping
    @Operation(summary = "Search products")
    public PageResponse<ProductResponse> search(
            @AuthenticationPrincipal CatalogJwtPrincipal principal,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return CatalogDtoMapper.toProductPage(service.searchVisible(
                query, categoryId, status, page, size, isAdministrator(principal)));
    }

    private static boolean isAdministrator(CatalogJwtPrincipal principal) {
        return principal != null && principal.roles().contains("ADMIN");
    }
}


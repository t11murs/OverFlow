package com.orderflow.catalog.adapter.inbound.rest;

import com.orderflow.catalog.api.CatalogDtos.CategoryResponse;
import com.orderflow.catalog.api.CatalogDtos.OfferResponse;
import com.orderflow.catalog.api.CatalogDtos.PageResponse;
import com.orderflow.catalog.api.CatalogDtos.ProductResponse;
import com.orderflow.catalog.application.port.PageResult;
import com.orderflow.catalog.domain.category.Category;
import com.orderflow.catalog.domain.offer.Offer;
import com.orderflow.catalog.domain.product.Product;

final class CatalogDtoMapper {

    private CatalogDtoMapper() {}

    static CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                category.id(),
                category.parentId(),
                category.name(),
                category.slug(),
                category.version(),
                category.createdAt(),
                category.updatedAt());
    }

    static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.id(),
                product.categoryId(),
                product.name(),
                product.description(),
                product.brand(),
                product.status(),
                product.version(),
                product.createdAt(),
                product.updatedAt());
    }

    static OfferResponse toResponse(Offer offer) {
        return new OfferResponse(
                offer.id(),
                offer.productId(),
                offer.sellerId(),
                offer.sellerSku(),
                offer.price().amount(),
                offer.price().currency(),
                offer.status(),
                offer.version(),
                offer.createdAt(),
                offer.updatedAt());
    }

    static PageResponse<ProductResponse> toProductPage(PageResult<Product> page) {
        return new PageResponse<>(
                page.content().stream().map(CatalogDtoMapper::toResponse).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }

    static PageResponse<OfferResponse> toOfferPage(PageResult<Offer> page) {
        return new PageResponse<>(
                page.content().stream().map(CatalogDtoMapper::toResponse).toList(),
                page.page(), page.size(), page.totalElements(), page.totalPages());
    }
}


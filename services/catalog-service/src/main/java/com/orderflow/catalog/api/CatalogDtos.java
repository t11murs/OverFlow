package com.orderflow.catalog.api;

import com.orderflow.catalog.domain.offer.OfferStatus;
import com.orderflow.catalog.domain.product.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CatalogDtos {

    private CatalogDtos() {}

    public record CategoryRequest(
            UUID parentId,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 180) String slug
    ) {}

    public record CategoryResponse(
            UUID id,
            UUID parentId,
            String name,
            String slug,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record ProductRequest(
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 255) String name,
            @Size(max = 20_000) String description,
            @Size(max = 160) String brand
    ) {}

    public record ProductStatusRequest(@NotNull ProductStatus status) {}

    public record ProductResponse(
            UUID id,
            UUID categoryId,
            String name,
            String description,
            String brand,
            ProductStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record OfferCreateRequest(
            @NotNull UUID productId,
            @NotBlank @Size(max = 100) String sellerSku,
            @NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal priceAmount,
            @NotBlank @Size(min = 3, max = 3) String currency
    ) {}

    public record OfferUpdateRequest(
            @NotBlank @Size(max = 100) String sellerSku,
            @NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal priceAmount,
            @NotBlank @Size(min = 3, max = 3) String currency
    ) {}

    public record OfferStatusRequest(@NotNull OfferStatus status) {}

    public record ResolveOffersRequest(
            @NotEmpty @Size(max = 100) List<@NotNull UUID> offerIds
    ) {}

    public record ResolvedOfferResponse(
            UUID offerId,
            UUID productId,
            UUID sellerId,
            String productName,
            String sellerSku,
            BigDecimal priceAmount,
            String currency
    ) {}

    public record OfferOwnershipResponse(UUID offerId, UUID sellerId) {}

    public record OfferResponse(
            UUID id,
            UUID productId,
            UUID sellerId,
            String sellerSku,
            BigDecimal priceAmount,
            String currency,
            OfferStatus status,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
        public PageResponse {
            content = List.copyOf(content);
        }
    }

    public record FieldViolation(String field, String message) {}

    public record ApiErrorResponse(
            Instant timestamp,
            int status,
            String code,
            String message,
            String path,
            List<FieldViolation> violations
    ) {
        public ApiErrorResponse {
            violations = List.copyOf(violations);
        }
    }
}


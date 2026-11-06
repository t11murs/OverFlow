package com.orderflow.catalog.adapter.inbound.rest;

import com.orderflow.catalog.api.CatalogDtos.OfferCreateRequest;
import com.orderflow.catalog.api.CatalogDtos.OfferOwnershipResponse;
import com.orderflow.catalog.api.CatalogDtos.OfferResponse;
import com.orderflow.catalog.api.CatalogDtos.OfferStatusRequest;
import com.orderflow.catalog.api.CatalogDtos.OfferUpdateRequest;
import com.orderflow.catalog.api.CatalogDtos.PageResponse;
import com.orderflow.catalog.api.CatalogDtos.ResolveOffersRequest;
import com.orderflow.catalog.api.CatalogDtos.ResolvedOfferResponse;
import com.orderflow.catalog.application.offer.OfferService;
import com.orderflow.catalog.application.offer.OfferService.CreateOfferCommand;
import com.orderflow.catalog.application.offer.OfferService.UpdateOfferCommand;
import com.orderflow.catalog.config.CatalogJwtPrincipal;
import com.orderflow.catalog.config.CatalogCacheConfiguration;
import com.orderflow.catalog.domain.offer.OfferStatus;
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
import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/catalog/offers")
@Tag(name = "Seller offers")
public class OfferController {

    private final OfferService service;

    public OfferController(OfferService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create a draft seller offer")
    public ResponseEntity<OfferResponse> create(
            @AuthenticationPrincipal CatalogJwtPrincipal principal,
            @Valid @RequestBody OfferCreateRequest request
    ) {
        OfferResponse response = CatalogDtoMapper.toResponse(service.create(new CreateOfferCommand(
                request.productId(), principal.sellerId(), request.sellerSku(), request.priceAmount(), request.currency())));
        return ResponseEntity.created(URI.create("/api/v1/catalog/offers/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @CacheEvict(cacheNames = CatalogCacheConfiguration.PUBLIC_OFFER_CACHE, key = "#id")
    @Operation(summary = "Update offer price and seller SKU")
    public OfferResponse update(
            @AuthenticationPrincipal CatalogJwtPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody OfferUpdateRequest request
    ) {
        return CatalogDtoMapper.toResponse(service.update(
                id, principal.sellerId(),
                new UpdateOfferCommand(request.sellerSku(), request.priceAmount(), request.currency())));
    }

    @PatchMapping("/{id}/status")
    @CacheEvict(cacheNames = CatalogCacheConfiguration.PUBLIC_OFFER_CACHE, key = "#id")
    @Operation(summary = "Change offer lifecycle status")
    public OfferResponse changeStatus(
            @AuthenticationPrincipal CatalogJwtPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody OfferStatusRequest request
    ) {
        return CatalogDtoMapper.toResponse(service.changeStatus(id, principal.sellerId(), request.status()));
    }

    @GetMapping("/{id}")
    @Cacheable(
            cacheNames = CatalogCacheConfiguration.PUBLIC_OFFER_CACHE,
            key = "#id",
            condition = "#principal == null"
    )
    @Operation(summary = "Get an offer")
    public OfferResponse get(
            @AuthenticationPrincipal CatalogJwtPrincipal principal,
            @PathVariable UUID id
    ) {
        return CatalogDtoMapper.toResponse(service.getVisible(
                id, principal == null ? null : principal.sellerId(), isAdministrator(principal)));
    }

    @GetMapping
    @Operation(summary = "Search seller offers")
    public PageResponse<OfferResponse> search(
            @AuthenticationPrincipal CatalogJwtPrincipal principal,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) UUID sellerId,
            @RequestParam(required = false) OfferStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return CatalogDtoMapper.toOfferPage(service.searchVisible(
                productId, sellerId, status, page, size,
                principal == null ? null : principal.sellerId(), isAdministrator(principal)));
    }

    @GetMapping("/{id}/ownership")
    @Operation(summary = "Verify offer ownership for Inventory Service")
    public OfferOwnershipResponse ownership(@PathVariable UUID id, @RequestParam UUID sellerId) {
        var offer = service.requireOwnership(id, sellerId);
        return new OfferOwnershipResponse(offer.id(), offer.sellerId());
    }

    @PostMapping("/resolve")
    @Operation(summary = "Resolve active offers for an order checkout")
    public List<ResolvedOfferResponse> resolve(@Valid @RequestBody ResolveOffersRequest request) {
        return service.resolveActive(request.offerIds()).stream()
                .map(offer -> new ResolvedOfferResponse(
                        offer.offerId(),
                        offer.productId(),
                        offer.sellerId(),
                        offer.productName(),
                        offer.sellerSku(),
                        offer.price().amount(),
                        offer.price().currency()))
                .toList();
    }

    private static boolean isAdministrator(CatalogJwtPrincipal principal) {
        return principal != null && principal.roles().contains("ADMIN");
    }
}


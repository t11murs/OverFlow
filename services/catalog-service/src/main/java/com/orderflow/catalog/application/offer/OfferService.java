package com.orderflow.catalog.application.offer;

import com.orderflow.catalog.application.port.OfferRepository;
import com.orderflow.catalog.application.port.PageResult;
import com.orderflow.catalog.application.port.ProductRepository;
import com.orderflow.catalog.domain.offer.Offer;
import com.orderflow.catalog.domain.offer.OfferStatus;
import com.orderflow.catalog.domain.product.Product;
import com.orderflow.catalog.domain.product.ProductStatus;
import com.orderflow.catalog.domain.shared.DomainRuleViolation;
import com.orderflow.catalog.domain.shared.Money;
import com.orderflow.catalog.exception.CatalogConflictException;
import com.orderflow.catalog.exception.CatalogNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OfferService {

    private final OfferRepository offerRepository;
    private final ProductRepository productRepository;
    private final Clock clock;

    public OfferService(OfferRepository offerRepository, ProductRepository productRepository, Clock clock) {
        this.offerRepository = offerRepository;
        this.productRepository = productRepository;
        this.clock = clock;
    }

    @Transactional
    public Offer create(CreateOfferCommand command) {
        getProduct(command.productId());
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        Offer offer = Offer.create(
                id,
                command.productId(),
                command.sellerId(),
                command.sellerSku(),
                new Money(command.priceAmount(), command.currency()),
                now);
        assertSellerSkuAvailable(offer.sellerId(), offer.sellerSku(), id);
        return offerRepository.save(offer);
    }

    @Transactional
    public Offer update(UUID id, UUID sellerId, UpdateOfferCommand command) {
        Offer offer = getOwned(id, sellerId);
        Money price = new Money(command.priceAmount(), command.currency());
        String normalizedSku = normalizeSellerSku(command.sellerSku());
        assertSellerSkuAvailable(offer.sellerId(), normalizedSku, id);
        offer.updateDetails(normalizedSku, price, clock.instant());
        return offerRepository.save(offer);
    }

    @Transactional
    public Offer changeStatus(UUID id, UUID sellerId, OfferStatus targetStatus) {
        Offer offer = getOwned(id, sellerId);
        if (targetStatus == OfferStatus.ACTIVE) {
            Product product = productRepository.findByIdForUpdate(offer.productId())
                    .orElseThrow(() -> new CatalogNotFoundException("PRODUCT_NOT_FOUND", "Product was not found"));
            if (product.status() != ProductStatus.ACTIVE) {
                throw new CatalogConflictException("PRODUCT_NOT_ACTIVE", "Offer can be activated only for an active product");
            }
            offer.activate(clock.instant());
        } else if (targetStatus == OfferStatus.SUSPENDED) {
            offer.suspend(clock.instant());
        } else if (targetStatus == OfferStatus.ARCHIVED) {
            offer.archive(clock.instant());
        } else {
            throw new CatalogConflictException("INVALID_OFFER_STATE_TRANSITION", "Offer cannot transition to DRAFT");
        }
        return offerRepository.save(offer);
    }

    @Transactional(readOnly = true)
    public Offer get(UUID id) {
        return getRequired(id);
    }

    @Transactional(readOnly = true)
    public Offer getVisible(UUID id, UUID viewerSellerId, boolean administrator) {
        Offer offer = getRequired(id);
        boolean sellerOwnsOffer = viewerSellerId != null && viewerSellerId.equals(offer.sellerId());
        if (!administrator && !sellerOwnsOffer && offer.status() != OfferStatus.ACTIVE) {
            throw new CatalogNotFoundException("OFFER_NOT_FOUND", "Offer was not found");
        }
        return offer;
    }

    @Transactional(readOnly = true)
    public PageResult<Offer> search(UUID productId, UUID sellerId, OfferStatus status, int page, int size) {
        validatePage(page, size);
        return offerRepository.search(productId, sellerId, status, page, size);
    }

    @Transactional(readOnly = true)
    public PageResult<Offer> searchVisible(
            UUID productId,
            UUID requestedSellerId,
            OfferStatus status,
            int page,
            int size,
            UUID viewerSellerId,
            boolean administrator
    ) {
        validatePage(page, size);
        if (administrator) {
            return offerRepository.search(productId, requestedSellerId, status, page, size);
        }
        boolean ownSellerView = viewerSellerId != null
                && (requestedSellerId == null || viewerSellerId.equals(requestedSellerId));
        if (ownSellerView) {
            return offerRepository.search(productId, viewerSellerId, status, page, size);
        }
        if (status != null && status != OfferStatus.ACTIVE) {
            return new PageResult<>(List.of(), page, size, 0, 0);
        }
        return offerRepository.search(productId, requestedSellerId, OfferStatus.ACTIVE, page, size);
    }

    @Transactional(readOnly = true)
    public Offer requireOwnership(UUID offerId, UUID sellerId) {
        Offer offer = getRequired(offerId);
        if (!offer.sellerId().equals(sellerId)) {
            throw new CatalogNotFoundException("OFFER_NOT_FOUND", "Offer was not found");
        }
        return offer;
    }

    @Transactional(readOnly = true)
    public List<ResolvedOffer> resolveActive(Collection<UUID> offerIds) {
        if (offerIds == null || offerIds.isEmpty() || offerIds.size() > 100) {
            throw new CatalogConflictException("INVALID_OFFER_BATCH", "Offer batch must contain between 1 and 100 IDs");
        }
        List<UUID> requestedIds = List.copyOf(new LinkedHashSet<>(offerIds));
        if (requestedIds.size() != offerIds.size()) {
            throw new CatalogConflictException("DUPLICATE_OFFER_ID", "Offer batch must not contain duplicate IDs");
        }
        Map<UUID, Offer> offers = offerRepository.findAllById(requestedIds).stream()
                .collect(Collectors.toMap(Offer::id, Function.identity()));
        if (offers.size() != requestedIds.size()) {
            throw new CatalogNotFoundException("OFFER_NOT_FOUND", "At least one offer was not found");
        }
        if (offers.values().stream().anyMatch(offer -> offer.status() != OfferStatus.ACTIVE)) {
            throw new CatalogConflictException("OFFER_NOT_ACTIVE", "Every resolved offer must be active");
        }
        Map<UUID, Product> products = productRepository.findAllById(
                        offers.values().stream().map(Offer::productId).collect(Collectors.toSet()))
                .stream()
                .collect(Collectors.toMap(Product::id, Function.identity()));
        return requestedIds.stream().map(id -> {
            Offer offer = offers.get(id);
            Product product = products.get(offer.productId());
            if (product == null || product.status() != ProductStatus.ACTIVE) {
                throw new CatalogConflictException("PRODUCT_NOT_ACTIVE", "Every resolved offer must reference an active product");
            }
            return new ResolvedOffer(
                    offer.id(), product.id(), offer.sellerId(), product.name(), offer.sellerSku(), offer.price());
        }).toList();
    }

    private Offer getRequired(UUID id) {
        return offerRepository.findById(id)
                .orElseThrow(() -> new CatalogNotFoundException("OFFER_NOT_FOUND", "Offer was not found"));
    }

    private Offer getOwned(UUID id, UUID sellerId) {
        Offer offer = getRequired(id);
        if (!offer.sellerId().equals(sellerId)) {
            throw new CatalogNotFoundException("OFFER_NOT_FOUND", "Offer was not found");
        }
        return offer;
    }

    private Product getProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new CatalogNotFoundException("PRODUCT_NOT_FOUND", "Product was not found"));
    }

    private void assertSellerSkuAvailable(UUID sellerId, String sellerSku, UUID excludedId) {
        if (offerRepository.existsBySellerIdAndSellerSkuAndIdNot(sellerId, sellerSku, excludedId)) {
            throw new CatalogConflictException("DUPLICATE_SELLER_SKU", "Seller SKU is already used");
        }
    }

    private static String normalizeSellerSku(String sellerSku) {
        if (sellerSku == null || sellerSku.isBlank() || sellerSku.trim().length() > 100) {
            throw new DomainRuleViolation("INVALID_SELLER_SKU", "Seller SKU must contain between 1 and 100 characters");
        }
        return sellerSku.trim();
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new CatalogConflictException("INVALID_PAGE", "Page must be non-negative and size must be between 1 and 100");
        }
    }

    public record CreateOfferCommand(
            UUID productId,
            UUID sellerId,
            String sellerSku,
            BigDecimal priceAmount,
            String currency
    ) {}

    public record UpdateOfferCommand(String sellerSku, BigDecimal priceAmount, String currency) {}

    public record ResolvedOffer(
            UUID offerId,
            UUID productId,
            UUID sellerId,
            String productName,
            String sellerSku,
            Money price
    ) {}
}


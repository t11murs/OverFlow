package com.orderflow.catalog.application.product;

import com.orderflow.catalog.application.port.CategoryRepository;
import com.orderflow.catalog.application.port.OfferRepository;
import com.orderflow.catalog.application.port.PageResult;
import com.orderflow.catalog.application.port.ProductRepository;
import com.orderflow.catalog.domain.offer.OfferStatus;
import com.orderflow.catalog.domain.product.Product;
import com.orderflow.catalog.domain.product.ProductStatus;
import com.orderflow.catalog.exception.CatalogConflictException;
import com.orderflow.catalog.exception.CatalogNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final OfferRepository offerRepository;
    private final Clock clock;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            OfferRepository offerRepository,
            Clock clock
    ) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.offerRepository = offerRepository;
        this.clock = clock;
    }

    @Transactional
    public Product create(CreateProductCommand command) {
        assertCategoryExists(command.categoryId());
        Instant now = clock.instant();
        Product product = Product.create(
                UUID.randomUUID(), command.categoryId(), command.name(), command.description(), command.brand(), now);
        return productRepository.save(product);
    }

    @Transactional
    public Product update(UUID id, UpdateProductCommand command) {
        Product product = getRequired(id);
        assertCategoryExists(command.categoryId());
        product.updateDetails(command.categoryId(), command.name(), command.description(), command.brand(), clock.instant());
        return productRepository.save(product);
    }

    @Transactional
    public Product changeStatus(UUID id, ProductStatus targetStatus) {
        Product product = productRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CatalogNotFoundException("PRODUCT_NOT_FOUND", "Product was not found"));
        if (targetStatus == ProductStatus.ACTIVE) {
            product.activate(clock.instant());
        } else if (targetStatus == ProductStatus.ARCHIVED) {
            if (offerRepository.existsByProductIdAndStatus(id, OfferStatus.ACTIVE)) {
                throw new CatalogConflictException("ACTIVE_OFFERS_EXIST", "Product with active offers cannot be archived");
            }
            product.archive(clock.instant());
        } else {
            throw new CatalogConflictException("INVALID_PRODUCT_STATE_TRANSITION", "Product cannot transition to DRAFT");
        }
        return productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public Product get(UUID id) {
        return getRequired(id);
    }

    @Transactional(readOnly = true)
    public Product getVisible(UUID id, boolean includeNonActive) {
        Product product = getRequired(id);
        if (!includeNonActive && product.status() != ProductStatus.ACTIVE) {
            throw new CatalogNotFoundException("PRODUCT_NOT_FOUND", "Product was not found");
        }
        return product;
    }

    @Transactional(readOnly = true)
    public PageResult<Product> search(String query, UUID categoryId, ProductStatus status, int page, int size) {
        validatePage(page, size);
        return productRepository.search(normalizeQuery(query), categoryId, status, page, size);
    }

    @Transactional(readOnly = true)
    public PageResult<Product> searchVisible(
            String query, UUID categoryId, ProductStatus status, int page, int size, boolean includeNonActive
    ) {
        validatePage(page, size);
        if (!includeNonActive && status != null && status != ProductStatus.ACTIVE) {
            return new PageResult<>(java.util.List.of(), page, size, 0, 0);
        }
        ProductStatus effectiveStatus = includeNonActive ? status : ProductStatus.ACTIVE;
        return productRepository.search(normalizeQuery(query), categoryId, effectiveStatus, page, size);
    }

    private Product getRequired(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new CatalogNotFoundException("PRODUCT_NOT_FOUND", "Product was not found"));
    }

    private void assertCategoryExists(UUID categoryId) {
        if (categoryRepository.findById(categoryId).isEmpty()) {
            throw new CatalogNotFoundException("CATEGORY_NOT_FOUND", "Category was not found");
        }
    }

    private static String normalizeQuery(String query) {
        return query == null || query.isBlank() ? null : query.trim();
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new CatalogConflictException("INVALID_PAGE", "Page must be non-negative and size must be between 1 and 100");
        }
    }

    public record CreateProductCommand(UUID categoryId, String name, String description, String brand) {}

    public record UpdateProductCommand(UUID categoryId, String name, String description, String brand) {}
}


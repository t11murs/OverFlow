package com.orderflow.catalog.application;

import com.orderflow.catalog.application.port.CategoryRepository;
import com.orderflow.catalog.application.port.OfferRepository;
import com.orderflow.catalog.application.port.ProductRepository;
import com.orderflow.catalog.application.product.ProductService;
import com.orderflow.catalog.domain.offer.OfferStatus;
import com.orderflow.catalog.domain.product.Product;
import com.orderflow.catalog.domain.product.ProductStatus;
import com.orderflow.catalog.exception.CatalogConflictException;
import com.orderflow.catalog.exception.CatalogNotFoundException;
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
class ProductServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private OfferRepository offerRepository;

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(
                productRepository,
                categoryRepository,
                offerRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void productWithActiveOffersCannotBeArchived() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create(productId, UUID.randomUUID(), "Phone", null, null, NOW);
        product.activate(NOW);
        when(productRepository.findByIdForUpdate(productId)).thenReturn(Optional.of(product));
        when(offerRepository.existsByProductIdAndStatus(productId, OfferStatus.ACTIVE)).thenReturn(true);

        assertThrows(CatalogConflictException.class,
                () -> service.changeStatus(productId, ProductStatus.ARCHIVED));
    }

    @Test
    void draftProductIsHiddenFromPublicReader() {
        UUID productId = UUID.randomUUID();
        Product product = Product.create(productId, UUID.randomUUID(), "Phone", null, null, NOW);
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThrows(CatalogNotFoundException.class, () -> service.getVisible(productId, false));
    }
}


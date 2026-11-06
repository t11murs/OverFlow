package com.orderflow.catalog.application;

import com.orderflow.catalog.application.offer.OfferService;
import com.orderflow.catalog.application.port.OfferRepository;
import com.orderflow.catalog.application.port.ProductRepository;
import com.orderflow.catalog.domain.offer.Offer;
import com.orderflow.catalog.domain.product.Product;
import com.orderflow.catalog.domain.shared.Money;
import com.orderflow.catalog.exception.CatalogConflictException;
import com.orderflow.catalog.exception.CatalogNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfferServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    private OfferRepository offerRepository;
    @Mock
    private ProductRepository productRepository;

    private OfferService service;

    @BeforeEach
    void setUp() {
        service = new OfferService(offerRepository, productRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void resolvesActiveOffersInRequestedOrder() {
        Product product = activeProduct();
        Offer first = activeOffer(product.id(), "SKU-1");
        Offer second = activeOffer(product.id(), "SKU-2");
        when(offerRepository.findAllById(List.of(second.id(), first.id()))).thenReturn(List.of(first, second));
        when(productRepository.findAllById(java.util.Set.of(product.id()))).thenReturn(List.of(product));

        var resolved = service.resolveActive(List.of(second.id(), first.id()));

        assertEquals(List.of(second.id(), first.id()), resolved.stream().map(OfferService.ResolvedOffer::offerId).toList());
    }

    @Test
    void rejectsInactiveOffer() {
        Product product = activeProduct();
        Offer draft = Offer.create(
                UUID.randomUUID(), product.id(), UUID.randomUUID(), "SKU-1",
                new Money(new BigDecimal("10.0000"), "RUB"), NOW);
        when(offerRepository.findAllById(List.of(draft.id()))).thenReturn(List.of(draft));

        assertThrows(CatalogConflictException.class, () -> service.resolveActive(List.of(draft.id())));
    }

    @Test
    void hidesOfferOwnedByAnotherSellerDuringUpdate() {
        Offer offer = Offer.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "SKU-1",
                new Money(new BigDecimal("10.0000"), "RUB"), NOW);
        when(offerRepository.findById(offer.id())).thenReturn(Optional.of(offer));

        var update = new OfferService.UpdateOfferCommand("SKU-2", new BigDecimal("20.0000"), "RUB");

        assertThrows(CatalogNotFoundException.class,
                () -> service.update(offer.id(), UUID.randomUUID(), update));
    }

    @Test
    void sellerCanReadOwnDraftButPublicReaderCannot() {
        UUID sellerId = UUID.randomUUID();
        Offer offer = Offer.create(
                UUID.randomUUID(), UUID.randomUUID(), sellerId, "SKU-1",
                new Money(new BigDecimal("10.0000"), "RUB"), NOW);
        when(offerRepository.findById(offer.id())).thenReturn(Optional.of(offer));

        assertEquals(offer.id(), service.getVisible(offer.id(), sellerId, false).id());
        assertThrows(CatalogNotFoundException.class,
                () -> service.getVisible(offer.id(), null, false));
    }

    private static Product activeProduct() {
        Product product = Product.create(UUID.randomUUID(), UUID.randomUUID(), "Product", null, null, NOW);
        product.activate(NOW);
        return product;
    }

    private static Offer activeOffer(UUID productId, String sku) {
        Offer offer = Offer.create(
                UUID.randomUUID(), productId, UUID.randomUUID(), sku,
                new Money(new BigDecimal("10.0000"), "RUB"), NOW);
        offer.activate(NOW);
        return offer;
    }
}


package com.orderflow.catalog.domain;

import com.orderflow.catalog.domain.offer.Offer;
import com.orderflow.catalog.domain.offer.OfferStatus;
import com.orderflow.catalog.domain.product.Product;
import com.orderflow.catalog.domain.product.ProductStatus;
import com.orderflow.catalog.domain.shared.DomainRuleViolation;
import com.orderflow.catalog.domain.shared.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CatalogDomainTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void productCanBeActivatedAndArchived() {
        Product product = Product.create(UUID.randomUUID(), UUID.randomUUID(), "Phone", null, "Brand", NOW);

        product.activate(NOW.plusSeconds(1));
        product.archive(NOW.plusSeconds(2));

        assertEquals(ProductStatus.ARCHIVED, product.status());
        assertThrows(DomainRuleViolation.class,
                () -> product.updateDetails(UUID.randomUUID(), "Changed", null, null, NOW.plusSeconds(3)));
    }

    @Test
    void suspendedOfferCanBeReactivated() {
        Offer offer = Offer.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "SKU-1",
                new Money(new BigDecimal("199.9900"), "rub"),
                NOW);

        offer.activate(NOW.plusSeconds(1));
        offer.suspend(NOW.plusSeconds(2));
        offer.activate(NOW.plusSeconds(3));

        assertEquals(OfferStatus.ACTIVE, offer.status());
        assertEquals("RUB", offer.price().currency());
    }

    @Test
    void moneyRejectsNegativeAmountsAndExcessiveScale() {
        assertThrows(DomainRuleViolation.class, () -> new Money(new BigDecimal("-0.01"), "RUB"));
        assertThrows(DomainRuleViolation.class, () -> new Money(new BigDecimal("1.00001"), "RUB"));
    }
}


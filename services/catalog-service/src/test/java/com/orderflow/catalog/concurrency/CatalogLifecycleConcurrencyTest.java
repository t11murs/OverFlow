package com.orderflow.catalog.concurrency;

import com.orderflow.catalog.application.category.CategoryService;
import com.orderflow.catalog.application.category.CategoryService.CreateCategoryCommand;
import com.orderflow.catalog.application.offer.OfferService;
import com.orderflow.catalog.application.offer.OfferService.CreateOfferCommand;
import com.orderflow.catalog.application.product.ProductService;
import com.orderflow.catalog.application.product.ProductService.CreateProductCommand;
import com.orderflow.catalog.domain.offer.OfferStatus;
import com.orderflow.catalog.domain.product.ProductStatus;
import com.orderflow.catalog.domain.shared.DomainRuleViolation;
import com.orderflow.catalog.exception.CatalogConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("test")
class CatalogLifecycleConcurrencyTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withStartupTimeout(java.time.Duration.ofMinutes(3));

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("management.health.redis.enabled", () -> "false");
    }

    @Autowired private CategoryService categoryService;
    @Autowired private ProductService productService;
    @Autowired private OfferService offerService;

    @Test
    void productArchiveAndOfferActivationCannotCommitAnInvalidCombination() throws Exception {
        UUID categoryId = categoryService.create(
                new CreateCategoryCommand(null, "Concurrency", "concurrency-" + UUID.randomUUID())).id();
        UUID productId = productService.create(
                new CreateProductCommand(categoryId, "Concurrent product", null, null)).id();
        productService.changeStatus(productId, ProductStatus.ACTIVE);
        UUID sellerId = UUID.randomUUID();
        UUID offerId = offerService.create(new CreateOfferCommand(
                productId,
                sellerId,
                "SKU-" + UUID.randomUUID(),
                new BigDecimal("100.0000"),
                "RUB")).id();

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<Boolean> archive = executor.submit(
                    () -> changeState(() -> productService.changeStatus(productId, ProductStatus.ARCHIVED), ready, start));
            Future<Boolean> activate = executor.submit(
                    () -> changeState(() -> offerService.changeStatus(offerId, sellerId, OfferStatus.ACTIVE), ready, start));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int successful = (archive.get(10, TimeUnit.SECONDS) ? 1 : 0)
                    + (activate.get(10, TimeUnit.SECONDS) ? 1 : 0);
            assertEquals(1, successful);
        }

        boolean invalidCombination = productService.get(productId).status() == ProductStatus.ARCHIVED
                && offerService.get(offerId).status() == OfferStatus.ACTIVE;
        assertFalse(invalidCombination);
    }

    private static boolean changeState(
            Runnable action,
            CountDownLatch ready,
            CountDownLatch start
    ) throws Exception {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            return false;
        }
        try {
            action.run();
            return true;
        } catch (CatalogConflictException | DomainRuleViolation exception) {
            return false;
        }
    }
}

package com.orderflow.order.application.order;

import com.orderflow.order.application.port.CatalogOfferClient;
import com.orderflow.order.application.port.CatalogOfferSnapshot;
import com.orderflow.order.application.port.OrderRepository;
import com.orderflow.order.application.port.OrderSummary;
import com.orderflow.order.application.port.PageResult;
import com.orderflow.order.config.MarketplaceProperties;
import com.orderflow.order.domain.model.CustomerOrder;
import com.orderflow.order.domain.model.OrderLineSnapshot;
import com.orderflow.order.domain.shared.DomainRuleViolation;
import com.orderflow.order.domain.shared.Money;
import com.orderflow.order.exception.OrderConflictException;
import com.orderflow.order.exception.OrderNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class OrderApplicationService {

    private final OrderRepository orderRepository;
    private final OrderCreationTransaction orderCreationTransaction;
    private final OrderCancellationCoordinator cancellationCoordinator;
    private final CatalogOfferClient catalogOfferClient;
    private final MarketplaceProperties marketplaceProperties;
    private final Clock clock;

    public OrderApplicationService(
            OrderRepository orderRepository,
            OrderCreationTransaction orderCreationTransaction,
            OrderCancellationCoordinator cancellationCoordinator,
            CatalogOfferClient catalogOfferClient,
            MarketplaceProperties marketplaceProperties,
            Clock clock
    ) {
        this.orderRepository = orderRepository;
        this.orderCreationTransaction = orderCreationTransaction;
        this.cancellationCoordinator = cancellationCoordinator;
        this.catalogOfferClient = catalogOfferClient;
        this.marketplaceProperties = marketplaceProperties;
        this.clock = clock;
    }

    public CustomerOrder create(CreateOrderCommand command) {
        validateCommand(command);
        String idempotencyKey = command.idempotencyKey().trim();
        String fingerprint = fingerprint(command);
        CustomerOrder existing = orderCreationTransaction
                .findExisting(command.customerId(), idempotencyKey, fingerprint)
                .orElse(null);
        if (existing != null) {
            return existing;
        }

        List<UUID> offerIds = command.items().stream().map(OrderItemCommand::offerId).toList();
        Map<UUID, CatalogOfferSnapshot> offers = catalogOfferClient.resolveActiveOffers(offerIds).stream()
                .collect(Collectors.toMap(CatalogOfferSnapshot::offerId, Function.identity()));
        if (offers.size() != offerIds.size()) {
            throw new OrderConflictException("CATALOG_RESPONSE_MISMATCH", "Catalog did not resolve every requested offer");
        }

        List<OrderLineSnapshot> lines = command.items().stream().map(item -> {
            CatalogOfferSnapshot offer = offers.get(item.offerId());
            if (offer == null) {
                throw new OrderConflictException("OFFER_NOT_AVAILABLE", "Requested offer is not available");
            }
            return new OrderLineSnapshot(
                    offer.productId(),
                    offer.offerId(),
                    offer.sellerId(),
                    offer.productName(),
                    offer.sellerSku(),
                    new Money(offer.priceAmount(), offer.currency()),
                    item.quantity());
        }).toList();

        CustomerOrder order = CustomerOrder.create(
                UUID.randomUUID(),
                command.customerId(),
                idempotencyKey,
                fingerprint,
                lines,
                marketplaceProperties.commissionRate(),
                clock.instant());
        try {
            return orderCreationTransaction.saveIfAbsent(order);
        } catch (DataIntegrityViolationException exception) {
            return orderCreationTransaction.findExisting(command.customerId(), idempotencyKey, fingerprint)
                    .orElseThrow(() -> exception);
        }
    }

    @Transactional(readOnly = true)
    public CustomerOrder get(UUID id, UUID customerId) {
        CustomerOrder order = getRequired(id);
        if (!order.customerId().equals(customerId)) {
            throw new OrderNotFoundException();
        }
        return order;
    }

    @Transactional(readOnly = true)
    public PageResult<OrderSummary> listByCustomer(UUID customerId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new DomainRuleViolation("INVALID_PAGE", "Page must be non-negative and size must be between 1 and 100");
        }
        return orderRepository.findByCustomerId(customerId, page, size);
    }

    public CustomerOrder requestCancellation(UUID id, UUID customerId) {
        return cancellationCoordinator.request(id, customerId);
    }

    private CustomerOrder getRequired(UUID id) {
        return orderRepository.findById(id).orElseThrow(OrderNotFoundException::new);
    }

    private static void validateCommand(CreateOrderCommand command) {
        if (command == null || command.customerId() == null) {
            throw new DomainRuleViolation("INVALID_CUSTOMER_ID", "Customer ID must not be null");
        }
        if (command.idempotencyKey() == null || command.idempotencyKey().isBlank()
                || command.idempotencyKey().trim().length() > 64) {
            throw new DomainRuleViolation("INVALID_IDEMPOTENCY_KEY", "Idempotency key must contain between 1 and 64 characters");
        }
        if (command.items() == null || command.items().isEmpty() || command.items().size() > 100) {
            throw new DomainRuleViolation("INVALID_ORDER_LINES", "Order must contain between 1 and 100 lines");
        }
        if (command.items().stream().anyMatch(java.util.Objects::isNull)) {
            throw new DomainRuleViolation("INVALID_ORDER_LINE", "Order item is invalid");
        }
        long distinctOffers = command.items().stream().map(OrderItemCommand::offerId).distinct().count();
        if (distinctOffers != command.items().size()) {
            throw new DomainRuleViolation("DUPLICATE_OFFER_ID", "Order must not contain duplicate offers");
        }
        command.items().forEach(item -> {
            if (item.offerId() == null || item.quantity() < 1 || item.quantity() > 10_000) {
                throw new DomainRuleViolation("INVALID_ORDER_LINE", "Order item is invalid");
            }
        });
    }

    private static String fingerprint(CreateOrderCommand command) {
        String canonical = command.customerId() + "|" + command.items().stream()
                .sorted(Comparator.comparing(item -> item.offerId().toString()))
                .map(item -> item.offerId() + ":" + item.quantity())
                .collect(Collectors.joining("|"));
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public record CreateOrderCommand(UUID customerId, String idempotencyKey, List<OrderItemCommand> items) {}

    public record OrderItemCommand(UUID offerId, long quantity) {}
}


package com.orderflow.catalog.domain.shared;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() < 0) {
            throw new DomainRuleViolation("NEGATIVE_MONEY", "Money amount must not be negative");
        }
        if (amount.scale() > 4) {
            throw new DomainRuleViolation("INVALID_MONEY_SCALE", "Money amount supports at most 4 decimal places");
        }
        if (currency == null || currency.isBlank()) {
            throw new DomainRuleViolation("INVALID_CURRENCY", "Currency must not be blank");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(currency);
        } catch (IllegalArgumentException exception) {
            throw new DomainRuleViolation("INVALID_CURRENCY", "Currency must be a valid ISO 4217 code");
        }
        amount = amount.stripTrailingZeros();
        if (amount.scale() < 0) {
            amount = amount.setScale(0);
        }
    }
}


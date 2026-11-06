package com.orderflow.order.domain.shared;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Locale;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() < 0 || amount.scale() > 4) {
            throw new DomainRuleViolation("INVALID_MONEY", "Money must be non-negative and use at most 4 decimal places");
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
        amount = amount.setScale(4, RoundingMode.UNNECESSARY);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money multiply(long multiplier) {
        if (multiplier < 0) {
            throw new DomainRuleViolation("INVALID_QUANTITY", "Multiplier must not be negative");
        }
        return new Money(amount.multiply(BigDecimal.valueOf(multiplier)), currency);
    }

    public static Money zero(String currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!currency.equals(other.currency)) {
            throw new DomainRuleViolation("CURRENCY_MISMATCH", "Money currencies must match");
        }
    }
}


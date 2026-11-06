package com.orderflow.order.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServiceJwtTokenProviderTest {

    private static final String SECRET = "test-only-service-secret-with-at-least-thirty-two-characters";
    private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);

    @Test
    void issuesShortLivedTokenForConfiguredAudience() {
        ServiceJwtTokenProvider provider = new ServiceJwtTokenProvider(
                SECRET,
                "order-service",
                "catalog-service",
                Duration.ofSeconds(30),
                Clock.fixed(NOW, ZoneOffset.UTC));

        var claims = Jwts.parser()
                .verifyWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .requireIssuer("order-service")
                .requireAudience("catalog-service")
                .build()
                .parseSignedClaims(provider.issue())
                .getPayload();

        assertEquals("order-service", claims.getSubject());
        assertEquals("service", claims.get("token_use", String.class));
        assertEquals(NOW.plusSeconds(30), claims.getExpiration().toInstant());
    }
}

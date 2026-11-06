package com.orderflow.order.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class ServiceJwtTokenProvider {

    private final SecretKey key;
    private final String issuer;
    private final String audience;
    private final Duration ttl;
    private final Clock clock;

    public ServiceJwtTokenProvider(
            @Value("${security.service-jwt.secret}") String secret,
            @Value("${security.service-jwt.issuer}") String issuer,
            @Value("${security.service-jwt.audience}") String audience,
            @Value("${security.service-jwt.ttl}") Duration ttl,
            Clock clock
    ) {
        if (secret.length() < 32) {
            throw new IllegalArgumentException("security.service-jwt.secret must contain at least 32 characters");
        }
        if (ttl.isNegative() || ttl.isZero() || ttl.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("security.service-jwt.ttl must be between 1 ms and 5 minutes");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.audience = audience;
        this.ttl = ttl;
        this.clock = clock;
    }

    public String issue() {
        Instant issuedAt = clock.instant();
        return Jwts.builder()
                .issuer(issuer)
                .subject(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plus(ttl)))
                .claim("token_use", "service")
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }
}

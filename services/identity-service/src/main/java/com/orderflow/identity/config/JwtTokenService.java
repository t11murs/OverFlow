package com.orderflow.identity.config;

import com.orderflow.identity.domain.SellerMembershipRole;
import com.orderflow.identity.domain.UserAccount;
import com.orderflow.identity.domain.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class JwtTokenService {

    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;

    public JwtTokenService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedAccessToken issue(UserAccount account) {
        return issue(account, null, null);
    }

    public IssuedAccessToken issueForSeller(
            UserAccount account,
            UUID sellerId,
            SellerMembershipRole sellerRole
    ) {
        return issue(account, sellerId, sellerRole);
    }

    private IssuedAccessToken issue(
            UserAccount account,
            UUID sellerId,
            SellerMembershipRole sellerRole
    ) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        var builder = Jwts.builder()
                .issuer(properties.issuer())
                .subject(account.id().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .claim("email", account.email())
                .claim("roles", account.roles().stream().map(Enum::name).sorted().toList());
        if (sellerId != null && sellerRole != null) {
            builder.claim("seller_id", sellerId.toString());
            builder.claim("seller_role", sellerRole.name());
        }
        String token = builder.signWith(key, Jwts.SIG.HS256).compact();
        return new IssuedAccessToken(token, expiresAt);
    }

    public JwtPrincipal verify(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(properties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Object rawRoles = claims.get("roles");
        if (!(rawRoles instanceof List<?> values)) {
            throw new IllegalArgumentException("JWT roles claim is missing");
        }
        Set<UserRole> roles = values.stream()
                .map(String::valueOf)
                .map(UserRole::valueOf)
                .collect(Collectors.toUnmodifiableSet());
        String sellerId = claims.get("seller_id", String.class);
        String sellerRole = claims.get("seller_role", String.class);
        if ((sellerId == null) != (sellerRole == null)) {
            throw new IllegalArgumentException("JWT seller context is incomplete");
        }
        String subject = claims.getSubject();
        if (subject == null) {
            throw new IllegalArgumentException("JWT subject is missing");
        }
        return new JwtPrincipal(
                UUID.fromString(subject),
                claims.get("email", String.class),
                roles,
                sellerId == null ? null : UUID.fromString(sellerId),
                sellerRole == null ? null : SellerMembershipRole.valueOf(sellerRole));
    }

    public record IssuedAccessToken(String value, Instant expiresAt) {}
}

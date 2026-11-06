package com.orderflow.inventory.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class InventoryJwtAuthenticationFilter extends OncePerRequestFilter {

    private final SecretKey key;
    private final String issuer;
    private final SecretKey serviceKey;
    private final String serviceIssuer;
    private final String serviceAudience;

    public InventoryJwtAuthenticationFilter(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer}") String issuer,
            @Value("${security.service-jwt.secret}") String serviceSecret,
            @Value("${security.service-jwt.issuer}") String serviceIssuer,
            @Value("${security.service-jwt.audience}") String serviceAudience
    ) {
        if (secret.length() < 32) {
            throw new IllegalArgumentException("security.jwt.secret must contain at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        if (serviceSecret.length() < 32) {
            throw new IllegalArgumentException("security.service-jwt.secret must contain at least 32 characters");
        }
        this.serviceKey = Keys.hmacShaKeyFor(serviceSecret.getBytes(StandardCharsets.UTF_8));
        this.serviceIssuer = serviceIssuer;
        this.serviceAudience = serviceAudience;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            try {
                authenticateUser(token);
            } catch (JwtException | IllegalArgumentException userTokenFailure) {
                try {
                    authenticateService(token);
                } catch (JwtException | IllegalArgumentException serviceTokenFailure) {
                    SecurityContextHolder.clearContext();
                }
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticateUser(String token) {
        Claims claims = Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                .parseSignedClaims(token).getPayload();
        Object rawRoles = claims.get("roles");
        if (!(rawRoles instanceof List<?> values)) {
            throw new IllegalArgumentException("JWT roles claim is missing");
        }
        Set<String> roles = values.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
        String rawSellerId = claims.get("seller_id", String.class);
        String sellerRole = claims.get("seller_role", String.class);
        if ((rawSellerId == null) != (sellerRole == null)) {
            throw new IllegalArgumentException("JWT seller context is incomplete");
        }
        if (sellerRole != null && !Set.of("OWNER", "MANAGER", "OPERATOR").contains(sellerRole)) {
            throw new IllegalArgumentException("JWT seller role is invalid");
        }
        String subject = claims.getSubject();
        if (subject == null) {
            throw new IllegalArgumentException("JWT subject is missing");
        }
        UUID sellerId = rawSellerId == null ? null : UUID.fromString(rawSellerId);
        InventoryJwtPrincipal principal = new InventoryJwtPrincipal(
                UUID.fromString(subject), claims.get("email", String.class), roles, sellerId, sellerRole);
        var authorities = new ArrayList<>(roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList());
        if (sellerId != null) {
            authorities.add(new SimpleGrantedAuthority("SELLER_CONTEXT"));
            authorities.add(new SimpleGrantedAuthority("SELLER_" + sellerRole));
        }
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, authorities));
    }

    private void authenticateService(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(serviceKey)
                .requireIssuer(serviceIssuer)
                .requireAudience(serviceAudience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!"service".equals(claims.get("token_use", String.class))
                || !serviceIssuer.equals(claims.getSubject())) {
            throw new IllegalArgumentException("Service JWT claims are invalid");
        }
        var authority = new SimpleGrantedAuthority("SERVICE_ORDER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(claims.getSubject(), null, List.of(authority)));
    }
}

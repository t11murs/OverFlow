package com.orderflow.order.config;

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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class OrderJwtAuthenticationFilter extends OncePerRequestFilter {

    private final SecretKey key;
    private final String issuer;

    public OrderJwtAuthenticationFilter(
            @Value("${security.jwt.secret}") String secret,
            @Value("${security.jwt.issuer}") String issuer
    ) {
        if (secret.length() < 32) {
            throw new IllegalArgumentException("security.jwt.secret must contain at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                Claims claims = Jwts.parser().verifyWith(key).requireIssuer(issuer).build()
                        .parseSignedClaims(authorization.substring(7)).getPayload();
                Object rawRoles = claims.get("roles");
                if (!(rawRoles instanceof List<?> values)) {
                    throw new IllegalArgumentException("JWT roles claim is missing");
                }
                Set<String> roles = values.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
                String sellerId = claims.get("seller_id", String.class);
                String sellerRole = claims.get("seller_role", String.class);
                if ((sellerId == null) != (sellerRole == null)) {
                    throw new IllegalArgumentException("JWT seller context is incomplete");
                }
                String subject = claims.getSubject();
                if (subject == null) {
                    throw new IllegalArgumentException("JWT subject is missing");
                }
                OrderJwtPrincipal principal = new OrderJwtPrincipal(
                        UUID.fromString(subject), claims.get("email", String.class), roles,
                        sellerId == null ? null : UUID.fromString(sellerId), sellerRole);
                var authorities = roles.stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList();
                SecurityContextHolder.getContext().setAuthentication(
                        new UsernamePasswordAuthenticationToken(principal, null, authorities));
            } catch (JwtException | IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}

package com.orderflow.catalog.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogJwtAuthenticationFilterTest {

    private static final String USER_SECRET = "test-only-user-secret-with-at-least-thirty-two-characters";
    private static final String SERVICE_SECRET = "test-only-service-secret-with-at-least-thirty-two-characters";

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesOrderServiceForCatalogAudience() throws Exception {
        apply(serviceToken("catalog-service"));

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("order-service", authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("SERVICE_ORDER")));
    }

    @Test
    void rejectsServiceTokenForAnotherAudience() throws Exception {
        apply(serviceToken("inventory-service"));

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void authenticatesInventoryServiceForCatalogAudience() throws Exception {
        apply(serviceToken("inventory-service", "catalog-service"));

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertEquals("inventory-service", authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("SERVICE_INVENTORY")));
    }

    private static void apply(String token) throws Exception {
        CatalogJwtAuthenticationFilter filter = new CatalogJwtAuthenticationFilter(
                USER_SECRET, "identity-service", SERVICE_SECRET,
                "order-service", "inventory-service", "catalog-service");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
    }

    private static String serviceToken(String audience) {
        return serviceToken("order-service", audience);
    }

    private static String serviceToken(String issuer, String audience) {
        Instant issuedAt = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(issuer)
                .audience().add(audience).and()
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(30)))
                .claim("token_use", "service")
                .signWith(Keys.hmacShaKeyFor(SERVICE_SECRET.getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
    }
}

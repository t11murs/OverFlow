package com.orderflow.identity.config;

import com.orderflow.identity.domain.SellerMembershipRole;
import com.orderflow.identity.domain.UserAccount;
import com.orderflow.identity.domain.UserRole;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenServiceTest {

    @Test
    void issuesAndVerifiesAccessToken() {
        Instant before = Instant.now();
        JwtProperties properties = new JwtProperties(
                "test-only-secret-with-at-least-thirty-two-characters",
                "identity-service", Duration.ofMinutes(15), Duration.ofDays(30));
        JwtTokenService service = new JwtTokenService(properties, Clock.systemUTC());
        UUID userId = UUID.randomUUID();
        UserAccount account = UserAccount.registerCustomer(userId, "customer@example.com", "hash", before);

        JwtTokenService.IssuedAccessToken token = service.issue(account);
        JwtPrincipal principal = service.verify(token.value());

        assertEquals(userId, principal.userId());
        assertEquals("customer@example.com", principal.email());
        assertTrue(principal.roles().contains(UserRole.CUSTOMER));
        assertTrue(!token.expiresAt().isBefore(before.plus(Duration.ofMinutes(15))));
        assertTrue(!token.expiresAt().isAfter(Instant.now().plus(Duration.ofMinutes(15))));
    }

    @Test
    void issuesAndVerifiesSellerContextAccessToken() {
        JwtProperties properties = new JwtProperties(
                "test-only-secret-with-at-least-thirty-two-characters",
                "identity-service", Duration.ofMinutes(15), Duration.ofDays(30));
        JwtTokenService service = new JwtTokenService(properties, Clock.systemUTC());
        UUID userId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UserAccount account = UserAccount.registerCustomer(
                userId, "seller@example.com", "hash", Instant.now());

        JwtTokenService.IssuedAccessToken token = service.issueForSeller(
                account, sellerId, SellerMembershipRole.MANAGER);
        JwtPrincipal principal = service.verify(token.value());

        assertEquals(userId, principal.userId());
        assertEquals(sellerId, principal.sellerId());
        assertEquals(SellerMembershipRole.MANAGER, principal.sellerRole());
        assertTrue(principal.roles().contains(UserRole.CUSTOMER));
    }
}

package com.orderflow.identity.application;

import com.orderflow.identity.application.auth.AuthService;
import com.orderflow.identity.application.port.RefreshTokenRepository;
import com.orderflow.identity.application.port.UserAccountRepository;
import com.orderflow.identity.config.JwtProperties;
import com.orderflow.identity.config.JwtTokenService;
import com.orderflow.identity.domain.RefreshToken;
import com.orderflow.identity.domain.UserAccount;
import com.orderflow.identity.exception.IdentityConflictException;
import com.orderflow.identity.exception.IdentityAuthenticationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");

    @Mock UserAccountRepository users;
    @Mock RefreshTokenRepository refreshTokens;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtTokenService jwtTokens;

    private AuthService service;

    @BeforeEach
    void setUp() {
        JwtProperties properties = new JwtProperties(
                "test-only-secret-with-at-least-thirty-two-characters",
                "identity-service", Duration.ofMinutes(15), Duration.ofDays(30));
        service = new AuthService(
                users, refreshTokens, passwordEncoder, jwtTokens, properties,
                new SecureRandom(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void registersCustomerAndIssuesTokenPair() {
        when(users.existsByEmail("customer@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password-123")).thenReturn("encoded-password");
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(refreshTokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokens.issue(any())).thenReturn(
                new JwtTokenService.IssuedAccessToken("access-token", NOW.plusSeconds(900)));

        AuthService.TokenPair result = service.register(" Customer@Example.com ", "password-123");

        assertEquals("customer@example.com", result.account().email());
        assertEquals("access-token", result.accessToken());
        assertNotNull(result.refreshToken());
        verify(passwordEncoder).encode("password-123");
    }

    @Test
    void rejectsAlreadyRegisteredEmail() {
        when(users.existsByEmail("customer@example.com")).thenReturn(true);

        assertThrows(IdentityConflictException.class,
                () -> service.register("customer@example.com", "password-123"));

        verify(users, never()).save(any());
    }

    @Test
    void translatesConcurrentEmailConflictToStableBusinessError() {
        when(users.existsByEmail("customer@example.com")).thenReturn(false);
        when(passwordEncoder.encode("password-123")).thenReturn("encoded-password");
        when(users.save(any())).thenThrow(new DataIntegrityViolationException("unique email"));

        IdentityConflictException exception = assertThrows(IdentityConflictException.class,
                () -> service.register("customer@example.com", "password-123"));

        assertEquals("EMAIL_ALREADY_REGISTERED", exception.code());
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void rotatesUsableRefreshToken() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        UserAccount account = UserAccount.registerCustomer(userId, "customer@example.com", "encoded", NOW);
        RefreshToken current = new RefreshToken(
                UUID.randomUUID(), userId, familyId, "hash", NOW.plusSeconds(3600), null, null,
                NOW.minusSeconds(60));
        when(refreshTokens.findFamilyIdByHash(anyString())).thenReturn(Optional.of(familyId));
        when(refreshTokens.findByHashForUpdate(anyString())).thenReturn(Optional.of(current));
        when(users.findById(userId)).thenReturn(Optional.of(account));
        when(refreshTokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtTokens.issue(account)).thenReturn(
                new JwtTokenService.IssuedAccessToken("new-access-token", NOW.plusSeconds(900)));

        AuthService.TokenPair result = service.refresh("valid-refresh-token");

        assertEquals("new-access-token", result.accessToken());
        assertEquals(NOW, current.revokedAt());
        assertNotNull(current.replacedByTokenId());
        verify(refreshTokens).save(org.mockito.ArgumentMatchers.argThat(
                token -> !token.id().equals(current.id()) && token.familyId().equals(familyId)));
        InOrder locking = inOrder(refreshTokens);
        locking.verify(refreshTokens).findFamilyIdByHash(anyString());
        locking.verify(refreshTokens).lockFamily(familyId);
        locking.verify(refreshTokens).findByHashForUpdate(anyString());
    }

    @Test
    void revokesTokenFamilyWhenRotatedTokenIsReused() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshToken reused = new RefreshToken(
                UUID.randomUUID(), userId, familyId, "hash", NOW.plusSeconds(3600),
                NOW.minusSeconds(30), UUID.randomUUID(), NOW.minusSeconds(120));
        when(refreshTokens.findFamilyIdByHash(anyString())).thenReturn(Optional.of(familyId));
        when(refreshTokens.findByHashForUpdate(anyString())).thenReturn(Optional.of(reused));

        assertThrows(IdentityAuthenticationException.class,
                () -> service.refresh("already-rotated-refresh-token"));

        verify(refreshTokens).revokeFamily(familyId, NOW);
        verify(users, never()).findById(any());
    }

    @Test
    void locksTokenFamilyBeforeLogoutRevocation() {
        UUID userId = UUID.randomUUID();
        UUID familyId = UUID.randomUUID();
        RefreshToken current = new RefreshToken(
                UUID.randomUUID(), userId, familyId, "hash", NOW.plusSeconds(3600),
                null, null, NOW.minusSeconds(60));
        when(refreshTokens.findFamilyIdByHash(anyString())).thenReturn(Optional.of(familyId));
        when(refreshTokens.findByHashForUpdate(anyString())).thenReturn(Optional.of(current));
        when(refreshTokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.logout("valid-refresh-token");

        assertEquals(NOW, current.revokedAt());
        InOrder locking = inOrder(refreshTokens);
        locking.verify(refreshTokens).findFamilyIdByHash(anyString());
        locking.verify(refreshTokens).lockFamily(familyId);
        locking.verify(refreshTokens).findByHashForUpdate(anyString());
    }

    @Test
    void keepsLogoutIdempotentForUnknownToken() {
        when(refreshTokens.findFamilyIdByHash(anyString())).thenReturn(Optional.empty());

        service.logout("unknown-refresh-token");

        verify(refreshTokens, never()).lockFamily(any());
        verify(refreshTokens, never()).findByHashForUpdate(anyString());
    }
}

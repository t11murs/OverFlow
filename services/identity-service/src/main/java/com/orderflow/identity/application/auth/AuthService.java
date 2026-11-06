package com.orderflow.identity.application.auth;

import com.orderflow.identity.application.port.RefreshTokenRepository;
import com.orderflow.identity.application.port.UserAccountRepository;
import com.orderflow.identity.config.JwtProperties;
import com.orderflow.identity.config.JwtTokenService;
import com.orderflow.identity.domain.RefreshToken;
import com.orderflow.identity.domain.UserAccount;
import com.orderflow.identity.exception.IdentityAuthenticationException;
import com.orderflow.identity.exception.IdentityConflictException;
import com.orderflow.identity.exception.IdentityValidationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService {

    private final UserAccountRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokens;
    private final JwtProperties jwtProperties;
    private final SecureRandom secureRandom;
    private final Clock clock;

    public AuthService(UserAccountRepository users, RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, JwtTokenService jwtTokens, JwtProperties jwtProperties,
                       SecureRandom secureRandom, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokens = jwtTokens;
        this.jwtProperties = jwtProperties;
        this.secureRandom = secureRandom;
        this.clock = clock;
    }

    @Transactional
    public TokenPair register(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        validatePassword(password);
        if (users.existsByEmail(normalizedEmail)) {
            throw new IdentityConflictException("EMAIL_ALREADY_REGISTERED", "Email is already registered");
        }
        Instant now = clock.instant();
        UserAccount account;
        try {
            account = users.save(UserAccount.registerCustomer(
                    UUID.randomUUID(), normalizedEmail, passwordEncoder.encode(password), now));
        } catch (DataIntegrityViolationException exception) {
            throw new IdentityConflictException(
                    "EMAIL_ALREADY_REGISTERED", "Email is already registered");
        }
        return issuePair(account, now);
    }

    @Transactional
    public TokenPair login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);
        UserAccount account = users.findByEmail(normalizedEmail)
                .orElseThrow(AuthService::invalidCredentials);
        if (!account.canAuthenticate() || !isValidPasswordShape(password)
                || !passwordEncoder.matches(password, account.passwordHash())) {
            throw invalidCredentials();
        }
        return issuePair(account, clock.instant());
    }

    @Transactional(noRollbackFor = IdentityAuthenticationException.class)
    public TokenPair refresh(String rawToken) {
        requireRefreshToken(rawToken);
        Instant now = clock.instant();
        String tokenHash = hash(rawToken);
        UUID familyId = refreshTokens.findFamilyIdByHash(tokenHash)
                .orElseThrow(AuthService::invalidRefreshToken);
        refreshTokens.lockFamily(familyId);
        RefreshToken current = refreshTokens.findByHashForUpdate(tokenHash)
                .filter(token -> token.familyId().equals(familyId))
                .orElseThrow(AuthService::invalidRefreshToken);
        if (!current.isUsableAt(now)) {
            if (current.replacedByTokenId() != null) {
                refreshTokens.revokeFamily(current.familyId(), now);
            }
            throw invalidRefreshToken();
        }
        UserAccount account = users.findById(current.userId())
                .filter(UserAccount::canAuthenticate)
                .orElseThrow(AuthService::invalidRefreshToken);

        String replacementValue = generateRefreshToken();
        RefreshToken replacement = new RefreshToken(
                UUID.randomUUID(), account.id(), current.familyId(), hash(replacementValue),
                now.plus(jwtProperties.refreshTokenTtl()),
                null, null, now);
        refreshTokens.save(replacement);
        current.revoke(now, replacement.id());
        refreshTokens.save(current);
        JwtTokenService.IssuedAccessToken accessToken = jwtTokens.issue(account);
        return new TokenPair(accessToken.value(), accessToken.expiresAt(), replacementValue, replacement.expiresAt(), account);
    }

    @Transactional
    public void logout(String rawToken) {
        requireRefreshToken(rawToken);
        String tokenHash = hash(rawToken);
        Optional<UUID> familyId = refreshTokens.findFamilyIdByHash(tokenHash);
        if (familyId.isEmpty()) {
            return;
        }
        refreshTokens.lockFamily(familyId.get());
        refreshTokens.findByHashForUpdate(tokenHash)
                .filter(token -> token.familyId().equals(familyId.get())).ifPresent(token -> {
            token.revoke(clock.instant(), null);
            refreshTokens.save(token);
        });
    }

    private TokenPair issuePair(UserAccount account, Instant now) {
        JwtTokenService.IssuedAccessToken accessToken = jwtTokens.issue(account);
        String refreshValue = generateRefreshToken();
        UUID refreshTokenId = UUID.randomUUID();
        RefreshToken refreshToken = new RefreshToken(
                refreshTokenId, account.id(), refreshTokenId, hash(refreshValue),
                now.plus(jwtProperties.refreshTokenTtl()),
                null, null, now);
        refreshTokens.save(refreshToken);
        return new TokenPair(accessToken.value(), accessToken.expiresAt(), refreshValue, refreshToken.expiresAt(), account);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String normalizeEmail(String email) {
        try {
            return UserAccount.normalizeEmail(email);
        } catch (IllegalArgumentException exception) {
            throw new IdentityValidationException("INVALID_EMAIL", "Email is invalid");
        }
    }

    private static void validatePassword(String password) {
        if (!isValidPasswordShape(password)) {
            throw new IdentityValidationException(
                    "INVALID_PASSWORD", "Password must contain between 8 and 72 UTF-8 bytes");
        }
    }

    private static boolean isValidPasswordShape(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            return false;
        }
        return password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    private static void requireRefreshToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > 512) {
            throw invalidRefreshToken();
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static IdentityAuthenticationException invalidCredentials() {
        return new IdentityAuthenticationException("INVALID_CREDENTIALS", "Email or password is invalid");
    }

    private static IdentityAuthenticationException invalidRefreshToken() {
        return new IdentityAuthenticationException("INVALID_REFRESH_TOKEN", "Refresh token is invalid or expired");
    }

    public record TokenPair(String accessToken, Instant accessTokenExpiresAt, String refreshToken,
                            Instant refreshTokenExpiresAt, UserAccount account) {}
}

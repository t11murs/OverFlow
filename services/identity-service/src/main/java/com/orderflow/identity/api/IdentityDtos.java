package com.orderflow.identity.api;

import com.orderflow.identity.domain.UserRole;
import com.orderflow.identity.domain.SellerMembershipRole;
import com.orderflow.identity.domain.SellerStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class IdentityDtos {
    private IdentityDtos() {}

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(min = 8, max = 72) String password
    ) {}

    public record LoginRequest(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 72) String password
    ) {}

    public record RefreshRequest(@NotBlank @Size(max = 512) String refreshToken) {}

    public record LogoutRequest(@NotBlank @Size(max = 512) String refreshToken) {}

    public record SellerContextRequest(@NotNull UUID sellerId) {}

    public record AccessTokenResponse(
            String tokenType,
            String accessToken,
            Instant accessTokenExpiresAt,
            UUID sellerId,
            SellerMembershipRole sellerRole
    ) {}

    public record TokenResponse(
            String tokenType,
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            UserResponse user
    ) {}

    public record UserResponse(UUID id, String email, Set<UserRole> roles) {
        public UserResponse { roles = Set.copyOf(roles); }
    }

    public record CreateSellerRequest(
            @NotBlank @Size(max = 255) String legalName,
            @NotBlank @Size(max = 160) String displayName
    ) {}

    public record AddSellerMemberRequest(@NotNull UUID userId, @NotNull SellerMembershipRole role) {}

    public record SellerResponse(
            UUID id,
            String legalName,
            String displayName,
            SellerStatus status,
            SellerMembershipRole membershipRole,
            long version,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record SellerMemberResponse(
            UUID userId,
            String email,
            SellerMembershipRole role,
            Instant memberSince
    ) {}

    public record FieldViolation(String field, String message) {}

    public record ApiErrorResponse(
            Instant timestamp, int status, String code, String message, String path, List<FieldViolation> violations
    ) {
        public ApiErrorResponse { violations = List.copyOf(violations); }
    }
}

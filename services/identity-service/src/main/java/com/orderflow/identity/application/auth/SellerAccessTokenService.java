package com.orderflow.identity.application.auth;

import com.orderflow.identity.application.port.SellerOrganizationRepository;
import com.orderflow.identity.application.port.UserAccountRepository;
import com.orderflow.identity.config.JwtTokenService;
import com.orderflow.identity.domain.SellerMembership;
import com.orderflow.identity.domain.SellerStatus;
import com.orderflow.identity.domain.UserAccount;
import com.orderflow.identity.exception.IdentityAuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class SellerAccessTokenService {

    private final UserAccountRepository users;
    private final SellerOrganizationRepository sellers;
    private final JwtTokenService tokens;

    public SellerAccessTokenService(
            UserAccountRepository users,
            SellerOrganizationRepository sellers,
            JwtTokenService tokens
    ) {
        this.users = users;
        this.sellers = sellers;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public SellerAccessToken issue(UUID userId, UUID sellerId) {
        UserAccount user = users.findById(userId)
                .filter(UserAccount::canAuthenticate)
                .orElseThrow(() -> new IdentityAuthenticationException(
                        "ACCOUNT_NOT_ACTIVE", "User account is not active"));
        var seller = sellers.findById(sellerId)
                .filter(organization -> organization.status() == SellerStatus.ACTIVE)
                .orElseThrow(() -> new AccessDeniedException("Seller organization is not available"));
        SellerMembership membership = sellers.findMembership(seller.id(), userId)
                .orElseThrow(() -> new AccessDeniedException("User is not a member of this seller"));
        JwtTokenService.IssuedAccessToken issued = tokens.issueForSeller(user, seller.id(), membership.role());
        return new SellerAccessToken(issued.value(), issued.expiresAt(), seller.id(), membership.role());
    }

    public record SellerAccessToken(
            String value,
            java.time.Instant expiresAt,
            UUID sellerId,
            com.orderflow.identity.domain.SellerMembershipRole sellerRole
    ) {}
}

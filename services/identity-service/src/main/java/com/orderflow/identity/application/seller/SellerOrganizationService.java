package com.orderflow.identity.application.seller;

import com.orderflow.identity.application.port.SellerOrganizationRepository;
import com.orderflow.identity.application.port.UserAccountRepository;
import com.orderflow.identity.domain.SellerMembership;
import com.orderflow.identity.domain.SellerMembershipRole;
import com.orderflow.identity.domain.SellerOrganization;
import com.orderflow.identity.domain.SellerStatus;
import com.orderflow.identity.domain.UserAccount;
import com.orderflow.identity.domain.UserRole;
import com.orderflow.identity.exception.IdentityConflictException;
import com.orderflow.identity.exception.IdentityNotFoundException;
import com.orderflow.identity.exception.IdentityValidationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SellerOrganizationService {

    private final SellerOrganizationRepository sellers;
    private final UserAccountRepository users;
    private final Clock clock;

    public SellerOrganizationService(SellerOrganizationRepository sellers, UserAccountRepository users, Clock clock) {
        this.sellers = sellers;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public SellerAccess create(UUID creatorId, String legalName, String displayName) {
        UserAccount creator = requireUser(creatorId);
        Instant now = clock.instant();
        SellerOrganization organization;
        try {
            organization = sellers.save(SellerOrganization.create(UUID.randomUUID(), legalName, displayName, now));
        } catch (IllegalArgumentException exception) {
            throw new IdentityValidationException("INVALID_SELLER_ORGANIZATION", exception.getMessage());
        }
        SellerMembership membership = sellers.saveMembership(new SellerMembership(
                organization.id(), creatorId, SellerMembershipRole.OWNER, now, now));
        creator.addRole(UserRole.SELLER_MEMBER, now);
        users.save(creator);
        return new SellerAccess(organization, membership.role());
    }

    @Transactional(readOnly = true)
    public List<SellerAccess> listForUser(UUID userId) {
        requireUser(userId);
        return sellers.findMembershipsByUserId(userId).stream()
                .map(membership -> new SellerAccess(requireSeller(membership.sellerId()), membership.role()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SellerMember> listMembers(UUID actorId, UUID sellerId) {
        requireMembership(sellerId, actorId);
        requireSeller(sellerId);
        return sellers.findMembershipsBySellerId(sellerId).stream()
                .map(membership -> {
                    UserAccount user = requireUser(membership.userId());
                    return new SellerMember(user.id(), user.email(), membership.role(), membership.createdAt());
                })
                .toList();
    }

    @Transactional
    public SellerMember addMember(UUID actorId, UUID sellerId, UUID userId, SellerMembershipRole role) {
        if (userId == null || role == null) {
            throw new IdentityValidationException("INVALID_SELLER_MEMBER", "User ID and membership role are required");
        }
        SellerOrganization seller = requireSeller(sellerId);
        if (seller.status() != SellerStatus.ACTIVE) {
            throw new IdentityConflictException("SELLER_NOT_ACTIVE", "Seller organization is not active");
        }
        SellerMembership actor = requireMembership(sellerId, actorId);
        requireMayAdd(actor.role(), role);
        UserAccount user = requireUser(userId);
        if (sellers.findMembership(sellerId, userId).isPresent()) {
            throw new IdentityConflictException("SELLER_MEMBER_EXISTS", "User is already a seller member");
        }
        Instant now = clock.instant();
        SellerMembership membership = sellers.saveMembership(
                new SellerMembership(sellerId, userId, role, now, now));
        user.addRole(UserRole.SELLER_MEMBER, now);
        users.save(user);
        return new SellerMember(user.id(), user.email(), membership.role(), membership.createdAt());
    }

    private static void requireMayAdd(SellerMembershipRole actorRole, SellerMembershipRole targetRole) {
        boolean allowed = actorRole == SellerMembershipRole.OWNER
                || (actorRole == SellerMembershipRole.MANAGER && targetRole == SellerMembershipRole.OPERATOR);
        if (!allowed) {
            throw new AccessDeniedException("Seller membership does not allow adding this role");
        }
    }

    private SellerMembership requireMembership(UUID sellerId, UUID userId) {
        return sellers.findMembership(sellerId, userId)
                .orElseThrow(() -> new AccessDeniedException("User is not a member of this seller"));
    }

    private SellerOrganization requireSeller(UUID sellerId) {
        return sellers.findById(sellerId)
                .orElseThrow(() -> new IdentityNotFoundException("SELLER_NOT_FOUND", "Seller organization was not found"));
    }

    private UserAccount requireUser(UUID userId) {
        return users.findById(userId)
                .orElseThrow(() -> new IdentityNotFoundException("USER_NOT_FOUND", "User was not found"));
    }

    public record SellerAccess(SellerOrganization organization, SellerMembershipRole membershipRole) {}
    public record SellerMember(UUID userId, String email, SellerMembershipRole role, Instant memberSince) {}
}

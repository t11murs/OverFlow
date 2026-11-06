package com.orderflow.identity.application;

import com.orderflow.identity.application.port.SellerOrganizationRepository;
import com.orderflow.identity.application.port.UserAccountRepository;
import com.orderflow.identity.application.seller.SellerOrganizationService;
import com.orderflow.identity.domain.SellerMembership;
import com.orderflow.identity.domain.SellerMembershipRole;
import com.orderflow.identity.domain.SellerOrganization;
import com.orderflow.identity.domain.UserAccount;
import com.orderflow.identity.domain.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SellerOrganizationServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T12:00:00Z");

    @Mock SellerOrganizationRepository sellers;
    @Mock UserAccountRepository users;

    private SellerOrganizationService service;

    @BeforeEach
    void setUp() {
        service = new SellerOrganizationService(sellers, users, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void creatorBecomesOwnerAndSellerMember() {
        UUID userId = UUID.randomUUID();
        UserAccount user = UserAccount.registerCustomer(userId, "owner@example.com", "hash", NOW);
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(sellers.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sellers.saveMembership(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        SellerOrganizationService.SellerAccess result = service.create(userId, "Legal name", "Shop");

        assertEquals(SellerMembershipRole.OWNER, result.membershipRole());
        assertTrue(user.roles().contains(UserRole.SELLER_MEMBER));
        verify(users).save(user);
    }

    @Test
    void managerCannotGrantManagerRole() {
        UUID sellerId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        SellerOrganization seller = SellerOrganization.create(sellerId, "Legal", "Shop", NOW);
        when(sellers.findById(sellerId)).thenReturn(Optional.of(seller));
        when(sellers.findMembership(sellerId, actorId)).thenReturn(Optional.of(new SellerMembership(
                sellerId, actorId, SellerMembershipRole.MANAGER, NOW, NOW)));

        assertThrows(AccessDeniedException.class,
                () -> service.addMember(actorId, sellerId, targetId, SellerMembershipRole.MANAGER));
    }
}

package com.orderflow.identity.adapter.inbound.rest;

import com.orderflow.identity.api.IdentityDtos.AddSellerMemberRequest;
import com.orderflow.identity.api.IdentityDtos.CreateSellerRequest;
import com.orderflow.identity.api.IdentityDtos.SellerMemberResponse;
import com.orderflow.identity.api.IdentityDtos.SellerResponse;
import com.orderflow.identity.application.seller.SellerOrganizationService;
import com.orderflow.identity.application.seller.SellerOrganizationService.SellerAccess;
import com.orderflow.identity.application.seller.SellerOrganizationService.SellerMember;
import com.orderflow.identity.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sellers")
@Tag(name = "Организации продавцов")
public class SellerOrganizationController {

    private final SellerOrganizationService service;

    public SellerOrganizationController(SellerOrganizationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Создать организацию продавца")
    ResponseEntity<SellerResponse> create(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody CreateSellerRequest request
    ) {
        SellerResponse response = toResponse(service.create(
                principal.userId(), request.legalName(), request.displayName()));
        return ResponseEntity.created(URI.create("/api/v1/sellers/" + response.id())).body(response);
    }

    @GetMapping
    @Operation(summary = "Получить организации текущего пользователя")
    List<SellerResponse> list(@AuthenticationPrincipal JwtPrincipal principal) {
        return service.listForUser(principal.userId()).stream().map(SellerOrganizationController::toResponse).toList();
    }

    @GetMapping("/{sellerId}/members")
    @Operation(summary = "Получить участников организации")
    List<SellerMemberResponse> listMembers(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID sellerId
    ) {
        return service.listMembers(principal.userId(), sellerId).stream()
                .map(SellerOrganizationController::toResponse).toList();
    }

    @PostMapping("/{sellerId}/members")
    @Operation(summary = "Добавить участника организации")
    ResponseEntity<SellerMemberResponse> addMember(
            @AuthenticationPrincipal JwtPrincipal principal,
            @PathVariable UUID sellerId,
            @Valid @RequestBody AddSellerMemberRequest request
    ) {
        SellerMemberResponse response = toResponse(service.addMember(
                principal.userId(), sellerId, request.userId(), request.role()));
        return ResponseEntity.created(URI.create(
                "/api/v1/sellers/" + sellerId + "/members/" + response.userId())).body(response);
    }

    private static SellerResponse toResponse(SellerAccess access) {
        var organization = access.organization();
        return new SellerResponse(
                organization.id(), organization.legalName(), organization.displayName(), organization.status(),
                access.membershipRole(), organization.version(), organization.createdAt(), organization.updatedAt());
    }

    private static SellerMemberResponse toResponse(SellerMember member) {
        return new SellerMemberResponse(member.userId(), member.email(), member.role(), member.memberSince());
    }
}

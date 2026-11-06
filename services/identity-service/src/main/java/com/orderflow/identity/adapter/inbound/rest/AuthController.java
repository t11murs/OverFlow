package com.orderflow.identity.adapter.inbound.rest;

import com.orderflow.identity.api.IdentityDtos.LoginRequest;
import com.orderflow.identity.api.IdentityDtos.LogoutRequest;
import com.orderflow.identity.api.IdentityDtos.RefreshRequest;
import com.orderflow.identity.api.IdentityDtos.RegisterRequest;
import com.orderflow.identity.api.IdentityDtos.TokenResponse;
import com.orderflow.identity.api.IdentityDtos.UserResponse;
import com.orderflow.identity.api.IdentityDtos.AccessTokenResponse;
import com.orderflow.identity.api.IdentityDtos.SellerContextRequest;
import com.orderflow.identity.application.auth.AuthService;
import com.orderflow.identity.application.auth.AuthService.TokenPair;
import com.orderflow.identity.application.auth.SellerAccessTokenService;
import com.orderflow.identity.config.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Аутентификация")
public class AuthController {

    private final AuthService service;
    private final SellerAccessTokenService sellerAccessTokens;

    public AuthController(AuthService service, SellerAccessTokenService sellerAccessTokens) {
        this.service = service;
        this.sellerAccessTokens = sellerAccessTokens;
    }

    @PostMapping("/register")
    @Operation(summary = "Зарегистрировать покупателя")
    ResponseEntity<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(service.register(request.email(), request.password())));
    }

    @PostMapping("/login")
    @Operation(summary = "Войти по электронной почте и паролю")
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return toResponse(service.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Обновить пару токенов")
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return toResponse(service.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Отозвать refresh-токен")
    ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        service.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/seller-context")
    @Operation(summary = "Выпустить access JWT для выбранной организации продавца")
    AccessTokenResponse sellerContext(
            @AuthenticationPrincipal JwtPrincipal principal,
            @Valid @RequestBody SellerContextRequest request
    ) {
        var token = sellerAccessTokens.issue(principal.userId(), request.sellerId());
        return new AccessTokenResponse(
                "Bearer", token.value(), token.expiresAt(), token.sellerId(), token.sellerRole());
    }

    private static TokenResponse toResponse(TokenPair pair) {
        return new TokenResponse(
                "Bearer", pair.accessToken(), pair.accessTokenExpiresAt(), pair.refreshToken(),
                pair.refreshTokenExpiresAt(),
                new UserResponse(pair.account().id(), pair.account().email(), pair.account().roles()));
    }
}

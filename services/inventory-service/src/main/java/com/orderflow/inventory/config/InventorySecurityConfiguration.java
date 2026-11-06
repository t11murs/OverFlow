package com.orderflow.inventory.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class InventorySecurityConfiguration {
    @Bean
    SecurityFilterChain inventorySecurityFilterChain(
            HttpSecurity http,
            InventoryJwtAuthenticationFilter jwtFilter
    ) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/v3/api-docs/**", "/swagger-ui/**",
                                "/swagger-ui.html", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/inventory/offers/*/availability")
                                .hasAuthority("SERVICE_ORDER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/inventory/warehouses")
                                .hasAnyAuthority("SELLER_OWNER", "SELLER_MANAGER")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/inventory/warehouses/**")
                                .hasAnyAuthority("SELLER_OWNER", "SELLER_MANAGER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/inventory/warehouses/**")
                                .hasAnyAuthority("SELLER_OWNER", "SELLER_MANAGER")
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/inventory/warehouses", "/api/v1/inventory/warehouses/**")
                                .hasAnyAuthority("SELLER_OWNER", "SELLER_MANAGER", "SELLER_OPERATOR")
                        .requestMatchers(HttpMethod.POST, "/api/v1/inventory/stock-adjustments")
                                .hasAnyAuthority("SELLER_OWNER", "SELLER_MANAGER", "SELLER_OPERATOR")
                        .anyRequest().denyAll())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}


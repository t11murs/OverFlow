package com.orderflow.inventory.adapter.outbound.catalog;

import com.orderflow.inventory.application.port.CatalogOfferOwnershipClient;
import com.orderflow.inventory.config.CatalogClientProperties;
import com.orderflow.inventory.config.CatalogServiceJwtTokenProvider;
import com.orderflow.inventory.exception.CatalogDependencyException;
import com.orderflow.inventory.exception.InventoryNotFoundException;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.MDC;

import java.net.http.HttpClient;
import java.util.UUID;

@Component
class CatalogOfferOwnershipHttpClient implements CatalogOfferOwnershipClient {

    private final RestClient restClient;
    private final CatalogServiceJwtTokenProvider serviceTokens;

    CatalogOfferOwnershipHttpClient(
            RestClient.Builder restClientBuilder,
            CatalogClientProperties properties,
            CatalogServiceJwtTokenProvider serviceTokens
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = restClientBuilder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get("correlationId");
                    if (correlationId != null && !correlationId.isBlank()) {
                        request.getHeaders().set("X-Correlation-Id", correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
        this.serviceTokens = serviceTokens;
    }

    @Override
    public void requireOwnedBySeller(UUID offerId, UUID sellerId) {
        try {
            OfferOwnershipResponse response = restClient.get()
                    .uri(builder -> builder
                            .path("/api/v1/catalog/offers/{offerId}/ownership")
                            .queryParam("sellerId", sellerId)
                            .build(offerId))
                    .headers(headers -> headers.setBearerAuth(serviceTokens.issue()))
                    .retrieve()
                    .body(OfferOwnershipResponse.class);
            if (response == null || !offerId.equals(response.offerId()) || !sellerId.equals(response.sellerId())) {
                throw new CatalogDependencyException(
                        "CATALOG_INVALID_RESPONSE", "Catalog Service returned an invalid ownership response", null);
            }
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 404) {
                throw new InventoryNotFoundException("OFFER_NOT_FOUND", "Offer was not found");
            }
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                throw new CatalogDependencyException(
                        "CATALOG_AUTHENTICATION_FAILED", "Catalog Service rejected service authentication", exception);
            }
            throw new CatalogDependencyException(
                    "CATALOG_UNAVAILABLE", "Catalog Service is temporarily unavailable", exception);
        } catch (ResourceAccessException exception) {
            throw new CatalogDependencyException(
                    "CATALOG_UNAVAILABLE", "Catalog Service is temporarily unavailable", exception);
        } catch (RestClientException exception) {
            throw new CatalogDependencyException(
                    "CATALOG_INVALID_RESPONSE", "Catalog Service returned an invalid response", exception);
        }
    }

    private record OfferOwnershipResponse(UUID offerId, UUID sellerId) {}
}

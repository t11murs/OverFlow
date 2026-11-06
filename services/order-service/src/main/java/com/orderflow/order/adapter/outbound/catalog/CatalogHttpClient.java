package com.orderflow.order.adapter.outbound.catalog;

import com.orderflow.order.application.port.CatalogOfferClient;
import com.orderflow.order.application.port.CatalogOfferSnapshot;
import com.orderflow.order.config.CatalogClientProperties;
import com.orderflow.order.config.ServiceJwtTokenProvider;
import com.orderflow.order.exception.CatalogDependencyException;
import com.orderflow.order.exception.OrderConflictException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.slf4j.MDC;

import java.net.http.HttpClient;
import java.util.List;
import java.util.UUID;

@Component
class CatalogHttpClient implements CatalogOfferClient {

    private final RestClient restClient;
    private final ServiceJwtTokenProvider serviceTokens;

    CatalogHttpClient(
            RestClient.Builder restClientBuilder,
            CatalogClientProperties properties,
            ServiceJwtTokenProvider serviceTokens
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        restClient = restClientBuilder.clone()
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
    public List<CatalogOfferSnapshot> resolveActiveOffers(List<UUID> offerIds) {
        try {
            List<ResolvedOfferResponse> response = restClient.post()
                    .uri("/api/v1/catalog/offers/resolve")
                    .headers(headers -> headers.setBearerAuth(serviceTokens.issue()))
                    .body(new ResolveOffersRequest(offerIds))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            if (response == null) {
                throw new CatalogDependencyException(
                        "CATALOG_EMPTY_RESPONSE", "Catalog returned an empty response", null);
            }
            return response.stream().map(item -> new CatalogOfferSnapshot(
                    item.offerId(),
                    item.productId(),
                    item.sellerId(),
                    item.productName(),
                    item.sellerSku(),
                    item.priceAmount(),
                    item.currency())).toList();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                throw new CatalogDependencyException(
                        "CATALOG_AUTHENTICATION_FAILED",
                        "Catalog Service rejected service authentication",
                        exception);
            }
            if (exception.getStatusCode().is4xxClientError()) {
                throw new OrderConflictException("OFFER_NOT_AVAILABLE", "At least one requested offer is unavailable");
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

    private record ResolveOffersRequest(List<UUID> offerIds) {}

    private record ResolvedOfferResponse(
            UUID offerId,
            UUID productId,
            UUID sellerId,
            String productName,
            String sellerSku,
            java.math.BigDecimal priceAmount,
            String currency
    ) {}
}


package com.orderflow.order.adapter.outbound.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

interface CustomerOrderJpaRepository extends JpaRepository<CustomerOrderEntity, UUID> {

    @EntityGraph(attributePaths = {"sellerOrders", "sellerOrders.items"})
    @Query("select distinct o from CustomerOrderEntity o where o.id = :id")
    Optional<CustomerOrderEntity> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"sellerOrders", "sellerOrders.items"})
    @Query("""
            select distinct o
            from CustomerOrderEntity o
            where o.customerId = :customerId and o.idempotencyKey = :idempotencyKey
            """)
    Optional<CustomerOrderEntity> findDetailedByCustomerIdAndIdempotencyKey(
            @Param("customerId") UUID customerId,
            @Param("idempotencyKey") String idempotencyKey);

    Page<CustomerOrderEntity> findByCustomerId(UUID customerId, Pageable pageable);
}


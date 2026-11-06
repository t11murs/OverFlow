package com.orderflow.inventory.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface WarehouseJpaRepository extends JpaRepository<WarehouseEntity, UUID> {
    List<WarehouseEntity> findBySellerIdOrderByAllocationPriorityAscIdAsc(UUID sellerId);
    boolean existsBySellerIdAndNameAndIdNot(UUID sellerId, String name, UUID id);
}


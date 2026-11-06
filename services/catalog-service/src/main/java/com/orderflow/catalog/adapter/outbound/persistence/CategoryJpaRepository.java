package com.orderflow.catalog.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

interface CategoryJpaRepository extends JpaRepository<CategoryEntity, UUID> {

    @Query(value = "SELECT 1 FROM pg_advisory_xact_lock(67982341027190411)", nativeQuery = true)
    Integer acquireHierarchyLock();

    List<CategoryEntity> findAllByOrderByNameAsc();

    boolean existsBySlugAndIdNot(String slug, UUID id);
}


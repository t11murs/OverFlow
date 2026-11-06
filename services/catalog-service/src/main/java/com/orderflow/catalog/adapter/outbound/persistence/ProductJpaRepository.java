package com.orderflow.catalog.adapter.outbound.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

interface ProductJpaRepository extends JpaRepository<ProductEntity, UUID>, JpaSpecificationExecutor<ProductEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select product from ProductEntity product where product.id = :id")
    Optional<ProductEntity> findByIdForUpdate(@Param("id") UUID id);
}


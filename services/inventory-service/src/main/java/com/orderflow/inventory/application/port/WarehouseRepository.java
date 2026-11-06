package com.orderflow.inventory.application.port;

import com.orderflow.inventory.domain.model.Warehouse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository {

    Optional<Warehouse> findById(UUID id);

    List<Warehouse> findBySellerId(UUID sellerId);

    boolean existsBySellerIdAndNameAndIdNot(UUID sellerId, String name, UUID excludedId);

    Warehouse save(Warehouse warehouse);
}


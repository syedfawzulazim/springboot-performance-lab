package com.sfazim.inventory.repository;

import com.sfazim.inventory.model.Inventory;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    boolean existsBySkuCodeAndQuantityIsGreaterThanEqual(String skuCode, Integer quantity);

    @Modifying
    @Transactional
    @Query("UPDATE Inventory i SET i.quantity = i.quantity - :quantity WHERE i.skuCode = :skuCode")
    int decreaseQuantity(String skuCode, Integer quantity);



    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.skuCode = :skuCode")
    Inventory findBySkuCodeForUpdate(String skuCode);
}

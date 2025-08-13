package com.sfazim.inventory.service;


import com.sfazim.inventory.model.Inventory;
import com.sfazim.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {
    private final InventoryRepository inventoryRepository;
    private final RedisLockService redisLockService;
    private final RedissonClient redissonClient;


    //  using pessimistic lock
//    @Transactional
//    public boolean isInStock(String skuCode, Integer quantity) {
//        // Acquire pessimistic lock on the inventory row
//        Inventory inventory = inventoryRepository.findBySkuCodeForUpdate(skuCode);
//
//        if (inventory != null && inventory.getQuantity() >= quantity) {
//            // Reduce stock within the same transaction
//            inventory.setQuantity(inventory.getQuantity() - quantity);
//            inventoryRepository.save(inventory); // Save updates the locked row
//            log.info("Stock reduced by {} for SKU: {}", quantity, skuCode);
//            return true;
//        } else {
//            log.warn("Insufficient stock for SKU: {} or SKU not found", skuCode);
//            return false;
//        }
//    }




    // using distributed locking with redis
    public boolean isInStock(String skuCode, Integer quantity) {
        String lockKey = "lock:inventory:" + skuCode;
        RLock lock = redissonClient.getLock(lockKey);

        boolean locked = false;
        try {
            // Try acquiring the lock with a wait time of 5s, and hold time of 10s
            locked = lock.tryLock(5, 10, TimeUnit.SECONDS);

            if (!locked) {
                log.warn("Could not acquire lock for SKU: {}", skuCode);
                return false; // Could also throw a custom exception if needed
            }

            log.info("Checking if item is available for SKU: {}", skuCode);
            boolean result = inventoryRepository.existsBySkuCodeAndQuantityIsGreaterThanEqual(skuCode, quantity);

            if (result) {
                // Reduce stock here while holding the lock
                reduceStockWithoutLock(skuCode, quantity);
            }

            return result;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while trying to acquire lock for SKU: {}", skuCode, e);
            return false;
        } finally {
            if (locked) {
                lock.unlock();
            }
        }
    }



    @Transactional()
    public boolean reduceStockWithoutLock(String skuCode, Integer quantity) {
        // Reduce stock (this can cause race conditions if called concurrently)
        int updatedRows = inventoryRepository.decreaseQuantity(skuCode, quantity);

        if (updatedRows > 0) {
            log.info("Stock reduced by {} for SKU: {}", quantity, skuCode);
            return true;
        } else {
            log.warn("Failed to reduce stock for SKU: {}", skuCode);
            return false;
        }
    }
}

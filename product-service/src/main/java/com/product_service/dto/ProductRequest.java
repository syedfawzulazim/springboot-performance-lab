package com.product_service.dto;

import org.springframework.util.Assert;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductRequest(
        String id,
        String name,
        String description,
        BigDecimal price
) {
}
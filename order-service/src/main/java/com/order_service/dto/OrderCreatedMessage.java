package com.order_service.dto;

public record OrderCreatedMessage(
        String orderNumber,
        String email,
        String firstName,
        String lastName
) {}

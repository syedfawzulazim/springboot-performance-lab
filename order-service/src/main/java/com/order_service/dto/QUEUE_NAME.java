package com.order_service.dto;

public enum QUEUE_NAME {
    ORDER_CREATED_QUEUE,
    ORDER_DLQ,
    ORDER_RECOVERY_QUEUE;
}
package com.order_service.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.order_service.client.InventoryClient;
import com.order_service.dto.OrderCreatedMessage;
import com.order_service.dto.QUEUE_NAME;
import com.order_service.dto.OrderRequest;
import com.order_service.model.Order;
import com.order_service.repository.OrderRepository;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderSender orderSender;
    private final InventoryClient inventoryClient;

    public void placeOrder(OrderRequest orderRequest){
        // simulate inventory service call
        var isProductInStock = inventoryClient.isInStock(orderRequest.skuCode(), orderRequest.quantity());
        if(isProductInStock){

            try{
                Order order = new Order();
                order.setOrderNumber(UUID.randomUUID().toString());
                order.setPrice(orderRequest.price());
                order.setSkuCode(orderRequest.skuCode());
                order.setQuantity(orderRequest.quantity());

                orderRepository.save(order);

                // send message to AWS SQS
                OrderCreatedMessage orderCreatedMessage = new OrderCreatedMessage(
                        order.getOrderNumber(),
                        orderRequest.userDetails().email(),
                        orderRequest.userDetails().firstName(),
                        orderRequest.userDetails().lastName()
                );

                //converting the msg to string
                ObjectMapper objectMapper = new ObjectMapper();
                String OrderCreatedMessageJson = objectMapper.writeValueAsString(orderCreatedMessage);

                log.info("Start - Sending OrderCreatedMessage to SQS : {}", orderCreatedMessage);

                orderSender.sendOrderNotification("ORDER_TOPIC", OrderCreatedMessageJson);

               /*
                orderSender.sendOrderMessage(String.valueOf(QUEUE_NAME.ORDER_CREATED_QUEUE), OrderCreatedMessageJson);
                orderSender.sendOrderMessage(String.valueOf(QUEUE_NAME.ORDER_SHIPPED_QUEUE), OrderCreatedMessageJson);
               */

                log.info("End - Finished OrderCreatedMessage to SQS") ;

            } catch (Exception e){
                throw new RuntimeException(e);
            }

        } else {
            throw new RuntimeException("Product with SkuCode " + orderRequest.skuCode() + " is not in stock");
        }
    }
}
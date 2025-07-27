package com.shipping_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipping_service.config.OrderCreatedMessage;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.MailException;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderShippingListener {

    @SqsListener(value ="${queue.name}")
    public void handleOrderMessage(Message<String> message) throws JsonProcessingException {
        String messageJson = message.getPayload();
        System.out.println("Message: " +messageJson);

        ObjectMapper mapper = new ObjectMapper();

        // Step 1: Unwrap the SNS envelope
        JsonNode root = mapper.readTree(messageJson);
        String actualMessageJson = root.get("Message").asText(); // this is the real payload
        System.out.println(actualMessageJson);

        //preparing shipping



        try {
            log.info("Order Shipped...!");
            Acknowledgement.acknowledge(message);
        } catch (MailException e) {
            log.error("Exception occurred when sending mail", e);
            throw new RuntimeException("Exception occurred when sending mail to springshop@email.com", e);
        }
    }
}

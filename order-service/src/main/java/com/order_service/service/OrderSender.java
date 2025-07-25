package com.order_service.service;

import com.order_service.dto.OrderCreatedMessage;
import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderSender {

    private final SqsTemplate sqsTemplate;

    public OrderSender(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
    }

    public void sendOrderMessage(String queueName, String messageJson) {
        try{
            SendResult<String> result = sqsTemplate.send(to -> to.queue(queueName).payload(messageJson));
            System.out.println("Message sent result: "+ result);
            System.out.println("Message sent to SQS["+queueName+"]: " + messageJson);
        } catch (Exception e) {
            System.out.println("Failed to send message to" +queueName+ " " +e.getMessage());
            // Optional: retry or store in fallback
        }
    }
}

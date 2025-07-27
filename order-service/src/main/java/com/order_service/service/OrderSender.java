package com.order_service.service;

import io.awspring.cloud.sns.core.SnsTemplate;
import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsAsyncClient;

import java.util.concurrent.CompletableFuture;

@Service
public class OrderSender {

    private final SqsTemplate sqsTemplate;
    private final SnsTemplate snsTemplate;
    private final SnsAsyncClient snsAsyncClient;

    public OrderSender(SqsTemplate sqsTemplate, SnsTemplate snsTemplate, SnsAsyncClient snsAsyncClient) {
        this.sqsTemplate = sqsTemplate;
        this.snsTemplate = snsTemplate;
        this.snsAsyncClient = snsAsyncClient;
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


    public void sendOrderNotification(String topicName, String messageJson) {
        try {
            // Get topic ARN asynchronously
            getTopicArn(topicName).thenAccept(topicArn -> {
                if (snsTemplate.topicExists(topicArn)) {
                    System.out.println("Topic exists: " + topicArn);

                    // Send message
                    snsAsyncClient.publish(request -> request.topicArn(topicArn).message(messageJson));
                    System.out.println("Message sent to SNS [" + topicName + "]: " + messageJson);
                } else {
                    System.err.println("Topic does not exist: " + topicArn);
                }
            }).exceptionally(ex -> {
                System.err.println("Failed to send message to topic " + topicName + ": " + ex.getMessage());
                return null;
            });
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
        }
    }

    private CompletableFuture<String> getTopicArn(String topicName) {
        return snsAsyncClient.listTopics()
                .thenApply(response -> response.topics().stream()
                        .map(topic -> topic.topicArn())
                        .filter(arn -> arn.endsWith(":" + topicName))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeException("Topic not found: " + topicName))
                );
    }

}

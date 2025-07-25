package com.order_service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.CreateQueueResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class SqsQueueInitializer {

    private final SqsAsyncClient sqsAsyncClient;
    private final String mainQueueName;
    private final String dlqQueueName = "ORDER_DLQ";

    private static final int MAX_RECEIVE_COUNT = 5; // Number of failed attempts before moving to DLQ
    private static final int LONG_POLLING_WAIT_TIME_SECONDS = 20; // Long polling wait time
    private static final int VISIBILITY_TIMEOUT_SECONDS = 60; // Visibility timeout


    public SqsQueueInitializer(SqsAsyncClient sqsAsyncClient, @Value("${queue.name}") String queueName) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.mainQueueName = queueName;
    }

    @PostConstruct
    public void createQueue() {
        createDlq()
                .thenCompose(this::getDlqArn)
                .thenCompose(this::createMainQueueWithDlq)
                .thenAccept(response -> System.out.println("Main queue created: " + response.queueUrl()))
                .exceptionally(ex -> {
                    System.err.println("Failed to create queues: " + ex.getMessage());
                    ex.getMessage();
                    return null;
                });
    }

    private CompletableFuture<CreateQueueResponse> createDlq() {
        CreateQueueRequest dlqRequest = CreateQueueRequest.builder()
                .queueName(dlqQueueName)
                .build();

        return sqsAsyncClient.createQueue(dlqRequest)
                .whenComplete((res, ex) -> {
                    if (ex == null) {
                        System.out.println("DLQ created: " + res.queueUrl());
                    } else {
                        System.err.println("DLQ creation failed: " + ex.getMessage());
                    }
                });
    }

    private CompletableFuture<String> getDlqArn(CreateQueueResponse dlqResponse) {
        String dlqUrl = dlqResponse.queueUrl();
        GetQueueAttributesRequest attrRequest = GetQueueAttributesRequest.builder()
                .queueUrl(dlqUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build();

        return sqsAsyncClient.getQueueAttributes(attrRequest)
                .thenApply(attrRes -> attrRes.attributes().get(QueueAttributeName.QUEUE_ARN));
    }

    private CompletableFuture<CreateQueueResponse> createMainQueueWithDlq(String dlqArn) {
        try {
            Map<QueueAttributeName, String> attributes = getQueueAttributeNameStringMap(dlqArn);

            CreateQueueRequest mainQueueRequest = CreateQueueRequest.builder()
                    .queueName(mainQueueName)
                    .attributes(attributes)
                    .build();

            return sqsAsyncClient.createQueue(mainQueueRequest);

        } catch (Exception e) {
            System.err.println("Error preparing redrive policy for main queue: " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }

    private static Map<QueueAttributeName, String> getQueueAttributeNameStringMap(String dlqArn) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> redrivePolicy = Map.of(
                "deadLetterTargetArn", dlqArn,
                "maxReceiveCount", MAX_RECEIVE_COUNT
        );
        String redrivePolicyJson = mapper.writeValueAsString(redrivePolicy);

        Map<QueueAttributeName, String> attributes = Map.of(
                QueueAttributeName.REDRIVE_POLICY, redrivePolicyJson,
                QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, String.valueOf(LONG_POLLING_WAIT_TIME_SECONDS),
                QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(VISIBILITY_TIMEOUT_SECONDS)
        );
        return attributes;
    }

}
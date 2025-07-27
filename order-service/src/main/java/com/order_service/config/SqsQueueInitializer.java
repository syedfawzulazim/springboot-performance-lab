package com.order_service.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Component("sqsQueueInitializer")
public class SqsQueueInitializer {

    private final SqsAsyncClient sqsAsyncClient;
    private final List<String> mainQueueNames;

    private static final int MAX_RECEIVE_COUNT = 5;
    private static final int LONG_POLLING_WAIT_TIME_SECONDS = 20;
    private static final int VISIBILITY_TIMEOUT_SECONDS = 3;

    public SqsQueueInitializer(SqsAsyncClient sqsAsyncClient,
                               @Value("${queue.names}") String queueNames) {
        this.sqsAsyncClient = sqsAsyncClient;
        this.mainQueueNames = Arrays.asList(queueNames.split(","));
    }

    @PostConstruct
    public void createQueues() {
        for (String mainQueueName : mainQueueNames) {
            String dlqName = mainQueueName + "_DLQ";

            CompletableFuture<Void> future =  createDlq(dlqName)
                    .thenCompose(dlqRes -> getDlqArn(dlqRes.queueUrl()))
                    .thenCompose(dlqArn -> createMainQueueWithDlq(mainQueueName, dlqArn))
                    .thenAccept(res -> System.out.println("Queue created: " + res.queueUrl()))
                    .exceptionally(ex -> {
                        System.err.println("Failed to create queues for " + mainQueueName + ": " + ex.getMessage());
                        return null;
                    });
            future.join();
        }
    }

    private CompletableFuture<CreateQueueResponse> createDlq(String dlqName) {
        CreateQueueRequest request = CreateQueueRequest.builder()
                .queueName(dlqName)
                .build();

        return sqsAsyncClient.createQueue(request)
                .whenComplete((res, ex) -> {
                    if (ex == null) {
                        System.out.println("DLQ created: " + res.queueUrl());
                    } else {
                        System.err.println("DLQ creation failed: " + ex.getMessage());
                    }
                });
    }

    private CompletableFuture<String> getDlqArn(String dlqUrl) {
        GetQueueAttributesRequest attrRequest = GetQueueAttributesRequest.builder()
                .queueUrl(dlqUrl)
                .attributeNames(QueueAttributeName.QUEUE_ARN)
                .build();

        return sqsAsyncClient.getQueueAttributes(attrRequest)
                .thenApply(attrs -> attrs.attributes().get(QueueAttributeName.QUEUE_ARN));
    }

    private CompletableFuture<CreateQueueResponse> createMainQueueWithDlq(String queueName, String dlqArn) {
        try {
            Map<QueueAttributeName, String> attributes = getQueueAttributes(dlqArn);
            CreateQueueRequest request = CreateQueueRequest.builder()
                    .queueName(queueName)
                    .attributes(attributes)
                    .build();
            return sqsAsyncClient.createQueue(request);

        } catch (JsonProcessingException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private static Map<QueueAttributeName, String> getQueueAttributes(String dlqArn) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        String redrivePolicy = mapper.writeValueAsString(Map.of(
                "deadLetterTargetArn", dlqArn,
                "maxReceiveCount", MAX_RECEIVE_COUNT
        ));

        return Map.of(
                QueueAttributeName.REDRIVE_POLICY, redrivePolicy,
                QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, String.valueOf(LONG_POLLING_WAIT_TIME_SECONDS),
                QueueAttributeName.VISIBILITY_TIMEOUT, String.valueOf(VISIBILITY_TIMEOUT_SECONDS)
        );
    }
}

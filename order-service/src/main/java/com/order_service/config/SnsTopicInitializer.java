package com.order_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsAsyncClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.SetQueueAttributesRequest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@DependsOn("sqsQueueInitializer") // Ensures this runs after SqsQueueInitializer
public class SnsTopicInitializer {

    private final SnsAsyncClient snsAsyncClient;
    private final SqsAsyncClient sqsAsyncClient;
    private final String topicName;
    private final List<String> queueNames;


    public SnsTopicInitializer(
            SnsAsyncClient snsAsyncClient, SqsAsyncClient sqsAsyncClient,
            @Value("${sns.topic.name}") String topicName,
            @Value("${queue.names}") String queueNames) {
        this.snsAsyncClient = snsAsyncClient;
        this.sqsAsyncClient = sqsAsyncClient;
        this.topicName = topicName;
        this.queueNames = Arrays.asList(queueNames.split(","));
    }

    @PostConstruct
    public void initialize() {
        createTopic()
                .thenCompose(this::subscribeQueues)
                .thenAccept(v -> System.out.println("SNS topic initialized and queues subscribed"))
                .exceptionally(ex -> {
                    System.err.println("SNS setup failed: " + ex.getMessage());
                    return null;
                });

    }

    private CompletableFuture<String> createTopic() {
        CreateTopicRequest topicRequest = CreateTopicRequest.builder()
                .name(topicName)
                .build();
        return snsAsyncClient.createTopic(topicRequest)
                .thenApply(CreateTopicResponse::topicArn)
                .whenComplete((arn, ex) -> {
                    if (ex == null) {
                        System.out.println("SNS topic created: " + arn);
                    } else {
                        System.err.println("SNS topic creation failed: " + ex.getMessage());
                    }
                });
    }

    private CompletableFuture<Void> subscribeQueues(String topicArn) {
        CompletableFuture<Void> allSubscriptions = CompletableFuture.completedFuture(null);

        for (String queueName : queueNames) {
            allSubscriptions = allSubscriptions.thenCompose(v ->
                    getQueueUrl(queueName)
                            .thenCompose(queueUrl -> {
                                if (queueUrl == null) return CompletableFuture.completedFuture(null);
                                return getQueueArn(queueUrl)
                                        .thenCompose(queueArn -> subscribeQueueToTopic(queueArn, topicArn, queueUrl));
                            })
            );
        }
        return allSubscriptions;
    }



    private CompletableFuture<String> getQueueArn(String queueUrl) {
        return sqsAsyncClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                        .queueUrl(queueUrl)
                        .attributeNames(QueueAttributeName.QUEUE_ARN)
                        .build())
                .thenApply(res -> res.attributes().get(QueueAttributeName.QUEUE_ARN));
    }

    private CompletableFuture<String> getQueueUrl(String queueName) {
        return sqsAsyncClient.getQueueUrl(builder -> builder.queueName(queueName))
                .thenApply(getQueueUrlResponse -> {
                    String queueUrl = getQueueUrlResponse.queueUrl();
                    System.out.println("Resolved queue URL for [" + queueName + "]: " + queueUrl);
                    return queueUrl;
                })
                .exceptionally(ex -> {
                    System.err.println("Failed to resolve queue URL for [" + queueName + "]: " + ex.getMessage());
                    return null;
                });
    }

    private CompletableFuture<Void> subscribeQueueToTopic(String queueArn, String topicArn, String queueUrl) {
        // to enable raw message sending
        Map<String, String> attributes = Map.of("RawMessageDelivery", "true");

        SubscribeRequest subscribeRequest = SubscribeRequest.builder()
                .topicArn(topicArn)
                .protocol("sqs")
                .endpoint(queueArn)
                .build();

        return snsAsyncClient.subscribe(subscribeRequest)
                .thenCompose(subRes -> setQueuePolicy(queueUrl, queueArn, topicArn));
    }

    private CompletableFuture<Void> setQueuePolicy(String queueUrl, String queueArn, String topicArn) {
        try {
            Map<String, Object> statement = Map.of(
                    "Effect", "Allow",
                    "Principal", "*",
                    "Action", "sqs:SendMessage",
                    "Resource", queueArn,
                    "Condition", Map.of("ArnEquals", Map.of("aws:SourceArn", topicArn))
            );

            ObjectMapper mapper = new ObjectMapper();
            String policyJson = mapper.writeValueAsString(Map.of(
                    "Version", "2012-10-17",
                    "Statement", List.of(statement)
            ));

            SetQueueAttributesRequest policyRequest = SetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributes(Map.of(QueueAttributeName.POLICY, policyJson))
                    .build();

            return sqsAsyncClient.setQueueAttributes(policyRequest)
                    .thenRun(() -> System.out.println("SQS policy set for queue: " + queueUrl))
                    .exceptionally(ex -> {
                        System.err.println("Failed to set SQS policy for queue " + queueUrl + ": " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            System.err.println("Error preparing SQS policy for queue " + queueUrl + ": " + e.getMessage());
            return CompletableFuture.failedFuture(e);
        }
    }


}
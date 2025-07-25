package com.order_service.service;

import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.awspring.cloud.sqs.operations.SendResult;
import org.springframework.stereotype.Service;

import java.util.Random;
@Service
public class OrderSenderWithRetry {

    private final SqsTemplate sqsTemplate;
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 100; // Initial delay
    private static final double JITTER_FACTOR = 0.1; // 10% jitter
    private static final Random RANDOM = new Random();

    public OrderSenderWithRetry(SqsTemplate sqsTemplate) {
        this.sqsTemplate = sqsTemplate;
    }

    public void sendOrderMessage(String queueName, String messageJson) {
        int attempt = 0;
        while (attempt <= MAX_RETRIES) {
            try {
                SendResult<String> result = sqsTemplate.send(to -> to.queue(queueName).payload(messageJson));
                System.out.println("Message sent result: " + result);
                System.out.println("Message sent to SQS[" + queueName + "]: " + messageJson);
                return; // Success, exit
            } catch (Exception e) {
                attempt++;
                if (attempt > MAX_RETRIES) {
                    System.err.println("Failed to send message to " + queueName + " after " + MAX_RETRIES + " attempts: " + e.getMessage());
                    // Fallback (e.g., store in a database or DLQ)
                    handleFallback(queueName, messageJson);
                    return;
                }
                // Calculate exponential backoff with jitter
                long delay = calculateBackoff(attempt);
                System.out.println("Retry attempt " + attempt + " for queue " + queueName + " after " + delay + "ms");
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    System.err.println("Interrupted during backoff: " + ie.getMessage());
                    return;
                }
            }
        }
    }

    private long calculateBackoff(int attempt) {
        // Exponential backoff: baseDelay * 2^(attempt-1) + random jitter
        long delay = (long) (BASE_DELAY_MS * Math.pow(2, attempt - 1));
        long jitter = (long) (delay * JITTER_FACTOR * RANDOM.nextDouble());
        return Math.min(delay + jitter, 10000); // Cap at 10 seconds
    }

    private void handleFallback(String queueName, String messageJson) {
        // Placeholder for fallback logic (e.g., store in database, log to file, or send to DLQ)
        System.err.println("Fallback: Storing message for queue " + queueName + ": " + messageJson);
        // Example: Save to a database or local file for later retry
    }
}
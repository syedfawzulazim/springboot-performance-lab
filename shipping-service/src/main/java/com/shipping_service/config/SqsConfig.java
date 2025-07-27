package com.shipping_service.config;

import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory;
import io.awspring.cloud.sqs.listener.acknowledgement.AcknowledgementResultCallback;
import io.awspring.cloud.sqs.listener.acknowledgement.handler.AcknowledgementMode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.Message;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

import java.net.URI;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collection;

@Configuration
@Slf4j
public class SqsConfig {

    @Value("${spring.cloud.aws.sqs.endpoint}")
    private String sqsEndpoint;

    @Value("${spring.cloud.aws.credentials.access-key}")
    private String accessKey;

    @Value("${spring.cloud.aws.credentials.secret-key}")
    private String secretKey;

    @Value("${spring.cloud.aws.region.static}")
    private String region;

    @Bean
    @Primary
    SqsAsyncClient sqsClient() {
        return SqsAsyncClient.builder()
                .endpointOverride(URI.create(sqsEndpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.of(region))
                .build();
    }

    @Bean
    SqsMessageListenerContainerFactory<Object> defaultSqsListenerContainerFactory(SqsAsyncClient sqsAsyncClient) {
        return SqsMessageListenerContainerFactory
                .builder()
                .configure(options -> options.acknowledgementMode(AcknowledgementMode.MANUAL)
                        .acknowledgementInterval(Duration.ofSeconds(3))
                        .acknowledgementThreshold(0)
                        .pollTimeout(Duration.ofSeconds(1))
                )
                .acknowledgementResultCallback(new AckResultCallback())
                .sqsAsyncClient(sqsAsyncClient)
                .build();
    }

    private static class AckResultCallback implements AcknowledgementResultCallback<Object>
    {
        @Override
        public void onSuccess(Collection<Message<Object>> messages) {
            log.info("ACK with success at {}", OffsetDateTime.now());
        }

        public void onFailure(Collection<Message<Object>> messages, Throwable t) {
            log.error("ACK with fail", t);
        }
    }
}

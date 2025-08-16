package com.sfazim.order.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sfazim.order.domain.model.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
public class RedisCacheConfig {

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        // Create ObjectMapper and register JavaTimeModule
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        // Optional: write dates as ISO-8601 strings instead of timestamps
        // mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        // Pass ObjectMapper directly to constructor
        Jackson2JsonRedisSerializer<User> serializer = new Jackson2JsonRedisSerializer<>(mapper, User.class);


        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(60))  // Default TTL: 60 seconds
                .disableCachingNullValues()  // Don't cache nulls
                        .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer));
            }

}
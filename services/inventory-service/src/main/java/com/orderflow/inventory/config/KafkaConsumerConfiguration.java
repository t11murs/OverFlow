package com.orderflow.inventory.config;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration(proxyBeanMethods = false)
public class KafkaConsumerConfiguration {

    @Bean
    CommonErrorHandler kafkaCommonErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaRetryProperties properties
    ) {
        var recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> new TopicPartition(
                        record.topic() + properties.dlqSuffix(),
                        record.partition()
                )
        );
        recoverer.setFailIfSendResultIsError(true);

        var errorHandler = new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(properties.retryDelay().toMillis(), properties.maxRetries())
        );
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
    }
}

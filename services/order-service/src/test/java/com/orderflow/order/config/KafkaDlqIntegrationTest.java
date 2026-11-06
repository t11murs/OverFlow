package com.orderflow.order.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.KafkaMessageListenerContainer;
import org.springframework.kafka.listener.MessageListener;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Testcontainers(disabledWithoutDocker = true)
class KafkaDlqIntegrationTest {

    private static final String CONTRACT_TOPIC = "orderflow.test.contract.v1";
    private static final String RETRY_TOPIC = "orderflow.test.retry.v1";
    private static final String DLQ_SUFFIX = ".dlq";

    @Container
    private static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.1")
            .withStartupTimeout(Duration.ofMinutes(3));

    private static DefaultKafkaProducerFactory<String, String> producerFactory;
    private static KafkaTemplate<String, String> kafkaTemplate;

    @BeforeAll
    static void setUpKafka() throws Exception {
        createTopics(
                CONTRACT_TOPIC,
                CONTRACT_TOPIC + DLQ_SUFFIX,
                RETRY_TOPIC,
                RETRY_TOPIC + DLQ_SUFFIX
        );
        producerFactory = new DefaultKafkaProducerFactory<>(producerProperties());
        kafkaTemplate = new KafkaTemplate<>(producerFactory);
    }

    @AfterAll
    static void closeProducer() {
        if (producerFactory != null) {
            producerFactory.destroy();
        }
    }

    @Test
    void contractErrorIsPublishedToDlqWithoutRetry() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        KafkaMessageListenerContainer<String, String> container = startListener(
                CONTRACT_TOPIC,
                3,
                attempts,
                () -> new IllegalArgumentException("invalid contract")
        );

        try {
            kafkaTemplate.send(CONTRACT_TOPIC, "order-1", "invalid-message").get(10, TimeUnit.SECONDS);

            ConsumerRecord<String, String> deadLetter = pollOne(CONTRACT_TOPIC + DLQ_SUFFIX);

            assertNotNull(deadLetter);
            assertEquals("order-1", deadLetter.key());
            assertEquals("invalid-message", deadLetter.value());
            assertEquals(1, attempts.get());
        } finally {
            container.stop();
        }
    }

    @Test
    void transientErrorIsPublishedToDlqAfterConfiguredRetries() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        KafkaMessageListenerContainer<String, String> container = startListener(
                RETRY_TOPIC,
                2,
                attempts,
                () -> new IllegalStateException("temporary failure")
        );

        try {
            kafkaTemplate.send(RETRY_TOPIC, "order-2", "retry-message").get(10, TimeUnit.SECONDS);

            ConsumerRecord<String, String> deadLetter = pollOne(RETRY_TOPIC + DLQ_SUFFIX);

            assertNotNull(deadLetter);
            assertEquals("order-2", deadLetter.key());
            assertEquals("retry-message", deadLetter.value());
            assertEquals(3, attempts.get());
        } finally {
            container.stop();
        }
    }

    private static KafkaMessageListenerContainer<String, String> startListener(
            String topic,
            int maxRetries,
            AtomicInteger attempts,
            Supplier<RuntimeException> failure
    ) throws InterruptedException {
        var consumerFactory = new DefaultKafkaConsumerFactory<String, String>(consumerProperties());
        var containerProperties = new ContainerProperties(topic);
        containerProperties.setAckMode(ContainerProperties.AckMode.RECORD);
        var container = new KafkaMessageListenerContainer<>(consumerFactory, containerProperties);
        var retryProperties = new KafkaRetryProperties(maxRetries, Duration.ofMillis(10), DLQ_SUFFIX);
        var errorHandler = new KafkaConsumerConfiguration()
                .kafkaCommonErrorHandler(kafkaTemplate, retryProperties);

        container.setCommonErrorHandler(errorHandler);
        container.setupMessageListener((MessageListener<String, String>) record -> {
            attempts.incrementAndGet();
            throw failure.get();
        });
        container.start();
        awaitAssignment(container);
        return container;
    }

    private static void awaitAssignment(KafkaMessageListenerContainer<String, String> container)
            throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (!hasAssignment(container) && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        if (!hasAssignment(container)) {
            container.stop();
            throw new IllegalStateException("Kafka listener did not receive a partition assignment");
        }
    }

    private static boolean hasAssignment(KafkaMessageListenerContainer<String, String> container) {
        var partitions = container.getAssignedPartitions();
        return partitions != null && !partitions.isEmpty();
    }

    private static ConsumerRecord<String, String> pollOne(String topic) {
        try (var consumer = new KafkaConsumer<String, String>(dlqConsumerProperties())) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (System.nanoTime() < deadline) {
                var records = consumer.poll(Duration.ofMillis(250)).records(topic);
                var iterator = records.iterator();
                if (iterator.hasNext()) {
                    return iterator.next();
                }
            }
            return null;
        }
    }

    private static void createTopics(String... topics) throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()
        ))) {
            List<NewTopic> definitions = List.of(topics).stream()
                    .map(topic -> new NewTopic(topic, 1, (short) 1))
                    .toList();
            admin.createTopics(definitions).all().get(10, TimeUnit.SECONDS);
        }
    }

    private static Map<String, Object> producerProperties() {
        return Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class
        );
    }

    private static Map<String, Object> consumerProperties() {
        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "listener-" + UUID.randomUUID(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );
    }

    private static Map<String, Object> dlqConsumerProperties() {
        return Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "dlq-reader-" + UUID.randomUUID(),
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class
        );
    }
}

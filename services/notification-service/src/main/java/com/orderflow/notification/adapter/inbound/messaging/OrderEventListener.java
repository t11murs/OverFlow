package com.orderflow.notification.adapter.inbound.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class OrderEventListener {

    private final OrderEventProcessor processor;
    private final ObjectMapper objectMapper;

    public OrderEventListener(OrderEventProcessor processor, ObjectMapper objectMapper) {
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${messaging.topics.order-events}")
    public void receive(ConsumerRecord<String, String> record) {
        try (KafkaLogContext.Scope ignored = KafkaLogContext.open(objectMapper, record)) {
            processor.process(record.key(), record.value());
        }
    }
}

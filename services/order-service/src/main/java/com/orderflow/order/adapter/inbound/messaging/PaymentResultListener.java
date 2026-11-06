package com.orderflow.order.adapter.inbound.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentResultListener {

    private final PaymentResultProcessor processor;
    private final ObjectMapper objectMapper;

    public PaymentResultListener(PaymentResultProcessor processor, ObjectMapper objectMapper) {
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${messaging.topics.payment-events}")
    public void receive(ConsumerRecord<String, String> record) {
        try (KafkaLogContext.Scope ignored = KafkaLogContext.open(objectMapper, record)) {
            processor.process(record.key(), record.value());
        }
    }
}

package com.orderflow.payment.adapter.inbound.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentCommandListener {

    private final PaymentCommandProcessor processor;
    private final ObjectMapper objectMapper;

    public PaymentCommandListener(PaymentCommandProcessor processor, ObjectMapper objectMapper) {
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${messaging.topics.payment-commands}")
    public void receive(ConsumerRecord<String, String> record) {
        try (KafkaLogContext.Scope ignored = KafkaLogContext.open(objectMapper, record)) {
            processor.process(record.key(), record.value());
        }
    }
}

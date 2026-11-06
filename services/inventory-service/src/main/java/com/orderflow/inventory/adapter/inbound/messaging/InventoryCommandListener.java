package com.orderflow.inventory.adapter.inbound.messaging;

import com.orderflow.inventory.domain.shared.DomainRuleViolation;
import com.orderflow.inventory.exception.InventoryConflictException;
import com.orderflow.inventory.exception.InventoryNotFoundException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class InventoryCommandListener {

    private final InventoryCommandProcessor processor;
    private final ObjectMapper objectMapper;

    public InventoryCommandListener(InventoryCommandProcessor processor, ObjectMapper objectMapper) {
        this.processor = processor;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${messaging.topics.inventory-commands}")
    public void receive(ConsumerRecord<String, String> record) {
        try (KafkaLogContext.Scope ignored = KafkaLogContext.open(objectMapper, record)) {
            receiveInContext(record);
        }
    }

    private void receiveInContext(ConsumerRecord<String, String> record) {
        String eventType = eventType(record.value());
        if ("ConfirmInventory".equals(eventType)) {
            confirm(record);
            return;
        }
        if ("ReleaseInventory".equals(eventType)) {
            processor.complete(eventType, record.key(), record.value());
            return;
        }
        if (!"ReserveInventory".equals(eventType)) {
            throw new IllegalArgumentException("Unsupported inventory command");
        }
        try {
            processor.reserve(record.key(), record.value());
        } catch (InventoryConflictException exception) {
            processor.reject(record.key(), record.value(), exception.code(), exception.getMessage());
        } catch (DomainRuleViolation exception) {
            processor.reject(record.key(), record.value(), exception.code(), exception.getMessage());
        }
    }

    private void confirm(ConsumerRecord<String, String> record) {
        try {
            processor.complete("ConfirmInventory", record.key(), record.value());
        } catch (InventoryConflictException exception) {
            processor.rejectConfirmation(record.key(), record.value(), exception.code(), exception.getMessage());
        } catch (InventoryNotFoundException exception) {
            processor.rejectConfirmation(record.key(), record.value(), exception.code(), exception.getMessage());
        } catch (DomainRuleViolation exception) {
            processor.rejectConfirmation(record.key(), record.value(), exception.code(), exception.getMessage());
        }
    }

    private String eventType(String rawMessage) {
        try {
            return objectMapper.readTree(rawMessage).path("eventType").asText();
        } catch (Exception exception) {
            throw new IllegalArgumentException("Inventory command is malformed", exception);
        }
    }
}

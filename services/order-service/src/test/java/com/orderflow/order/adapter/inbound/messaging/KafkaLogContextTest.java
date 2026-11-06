package com.orderflow.order.adapter.inbound.messaging;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaLogContextTest {
    @Test
    void addsEnvelopeFieldsAndRestoresPreviousContext() {
        ConsumerRecord<String, String> record = new ConsumerRecord<>("orders", 2, 10L, "key", """
                {
                  "eventId":"20000000-0000-0000-0000-000000000001",
                  "eventType":"OrderConfirmed",
                  "correlationId":"10000000-0000-0000-0000-000000000001"
                }
                """);
        MDC.put("correlationId", "outer-request");
        try {
            try (KafkaLogContext.Scope ignored = KafkaLogContext.open(new ObjectMapper(), record)) {
                assertThat(MDC.get("eventType")).isEqualTo("OrderConfirmed");
                assertThat(MDC.get("correlationId"))
                        .isEqualTo("10000000-0000-0000-0000-000000000001");
                assertThat(MDC.get("kafkaTopic")).isEqualTo("orders");
                assertThat(MDC.get("kafkaPartition")).isEqualTo("2");
            }

            assertThat(MDC.get("correlationId")).isEqualTo("outer-request");
            assertThat(MDC.get("eventType")).isNull();
        } finally {
            MDC.clear();
        }
    }
}

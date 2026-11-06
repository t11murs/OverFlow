package com.orderflow.payment.adapter.outbound.persistence;

import com.orderflow.payment.application.payment.PaymentResultRecorder;
import com.orderflow.payment.application.payment.RefundResultRecorder;
import com.orderflow.quality.KafkaContractAssertions;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentProducerContractTest {
    @Test
    void actualOutboxSerializationMatchesPublishedSchema() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(1);
        JdbcPaymentMessageStore store = new JdbcPaymentMessageStore(jdbc, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-10-27T10:15:30Z"), ZoneOffset.UTC));
        UUID orderId = UUID.randomUUID();

        store.append("Payment", UUID.randomUUID(), "PaymentCaptured", 1, "payment-events",
                orderId.toString(), orderId, UUID.randomUUID(),
                new PaymentResultRecorder.PaymentCaptured(orderId, UUID.randomUUID(), "provider-reference"));
        store.append("Payment", UUID.randomUUID(), "PaymentFailed", 1, "payment-events",
                orderId.toString(), orderId, UUID.randomUUID(),
                new PaymentResultRecorder.PaymentFailed(
                        orderId, UUID.randomUUID(), "PAYMENT_DECLINED", "Declined"));
        store.append("Refund", UUID.randomUUID(), "PaymentRefunded", 1, "payment-events",
                orderId.toString(), orderId, UUID.randomUUID(),
                new RefundResultRecorder.PaymentRefunded(
                        orderId, UUID.randomUUID(), UUID.randomUUID(), "refund-reference"));

        ArgumentCaptor<MapSqlParameterSource> parameters = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        verify(jdbc, org.mockito.Mockito.times(3)).update(anyString(), parameters.capture());
        for (MapSqlParameterSource message : parameters.getAllValues()) {
            KafkaContractAssertions.assertValid("payment-event-message-v1.schema.json",
                    (String) message.getValue("payload"));
        }
    }
}

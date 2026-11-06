package com.orderflow.tools.dlq;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DlqReplayOptionsTest {

    @Test
    void parsesSafeDryRunAndDerivesTargetTopic() {
        DlqReplayOptions options = DlqReplayOptions.parse(new String[]{
                "--topic", "orderflow.payment.commands.v1.dlq",
                "--partition", "2",
                "--offset", "42"
        });

        assertEquals("orderflow.payment.commands.v1", options.targetTopic());
        assertEquals(2, options.partition());
        assertEquals(42, options.offset());
        assertFalse(options.execute());
    }

    @Test
    void executeRequiresHashOperatorAndReason() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> DlqReplayOptions.parse(new String[]{
                        "--topic", "orderflow.payment.commands.v1.dlq",
                        "--partition", "0",
                        "--offset", "1",
                        "--execute"
                }));

        assertTrue(failure.getMessage().contains("requires"));
    }

    @Test
    void rejectsTopicOutsideOrderFlowDlqNamespace() {
        assertThrows(IllegalArgumentException.class, () -> DlqReplayOptions.parse(new String[]{
                "--topic", "unrelated.events.dlq",
                "--partition", "0",
                "--offset", "1"
        }));
    }

    @Test
    void rejectsNestedDlqTopic() {
        assertThrows(IllegalArgumentException.class, () -> DlqReplayOptions.parse(new String[]{
                "--topic", "orderflow.payment.commands.v1.dlq.dlq",
                "--partition", "0",
                "--offset", "1"
        }));
    }

    @Test
    void calculatesStablePayloadHash() {
        assertEquals(
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                DlqReplayCommand.sha256("abc".getBytes(StandardCharsets.UTF_8))
        );
    }
}

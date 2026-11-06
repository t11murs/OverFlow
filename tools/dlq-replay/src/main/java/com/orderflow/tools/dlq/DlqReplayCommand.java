package com.orderflow.tools.dlq;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

final class DlqReplayCommand {

    int run(String[] args, Map<String, String> environment, PrintStream output, PrintStream error) {
        if (containsHelp(args)) {
            output.println(usage());
            return 0;
        }

        try {
            DlqReplayOptions options = DlqReplayOptions.parse(args);
            Properties baseProperties = kafkaProperties(options, environment);
            DlqRecord record = readRecord(options, baseProperties);
            if (record.value() == null) {
                throw new IllegalArgumentException("Kafka tombstone records cannot be replayed");
            }
            String payloadSha256 = sha256(record.value());

            if (!options.execute()) {
                output.println(auditJson("INSPECTED", options, record, payloadSha256, null));
                return 0;
            }
            if (!MessageDigest.isEqual(
                    payloadSha256.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    options.expectedSha256().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                throw new IllegalArgumentException("Expected SHA-256 does not match the selected DLQ record");
            }

            ReplayResult result = replay(options, baseProperties, record);
            output.println(auditJson("REPLAYED", options, record, payloadSha256, result));
            return 0;
        } catch (Exception exception) {
            error.println("DLQ replay failed: " + safeMessage(exception));
            return 2;
        }
    }

    private static DlqRecord readRecord(DlqReplayOptions options, Properties baseProperties) throws Exception {
        validateTopics(options, baseProperties);
        Properties consumerProperties = copy(baseProperties);
        consumerProperties.put(ConsumerConfig.CLIENT_ID_CONFIG, "orderflow-dlq-inspector-" + UUID.randomUUID());
        consumerProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        consumerProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);
        consumerProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        TopicPartition source = new TopicPartition(options.sourceTopic(), options.partition());
        try (var consumer = new KafkaConsumer<byte[], byte[]>(consumerProperties)) {
            consumer.assign(List.of(source));
            long beginning = consumer.beginningOffsets(List.of(source), options.timeout()).get(source);
            long end = consumer.endOffsets(List.of(source), options.timeout()).get(source);
            if (options.offset() < beginning || options.offset() >= end) {
                throw new IllegalArgumentException(
                        "Offset is outside the retained range [" + beginning + ", " + end + ")");
            }
            consumer.seek(source, options.offset());
            long deadline = System.nanoTime() + options.timeout().toNanos();
            while (System.nanoTime() < deadline) {
                for (ConsumerRecord<byte[], byte[]> record : consumer.poll(java.time.Duration.ofMillis(250))) {
                    if (record.topic().equals(options.sourceTopic())
                            && record.partition() == options.partition()
                            && record.offset() == options.offset()) {
                        return new DlqRecord(record.key(), record.value(), record.timestamp());
                    }
                    if (record.offset() > options.offset()) {
                        break;
                    }
                }
            }
            throw new IllegalStateException("Selected DLQ record was not returned before timeout");
        }
    }

    private static ReplayResult replay(
            DlqReplayOptions options,
            Properties baseProperties,
            DlqRecord record
    ) throws Exception {
        Properties producerProperties = copy(baseProperties);
        producerProperties.put(ProducerConfig.CLIENT_ID_CONFIG, "orderflow-dlq-replay-" + UUID.randomUUID());
        producerProperties.put(ProducerConfig.ACKS_CONFIG, "all");
        producerProperties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        producerProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        producerProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);

        try (var producer = new KafkaProducer<byte[], byte[]>(producerProperties)) {
            var replay = new ProducerRecord<byte[], byte[]>(
                    options.targetTopic(), options.partition(), record.key(), record.value());
            var metadata = producer.send(replay).get(options.timeout().toMillis(), TimeUnit.MILLISECONDS);
            return new ReplayResult(metadata.partition(), metadata.offset());
        }
    }

    private static void validateTopics(DlqReplayOptions options, Properties baseProperties) throws Exception {
        Properties adminProperties = copy(baseProperties);
        adminProperties.put(AdminClientConfig.CLIENT_ID_CONFIG, "orderflow-dlq-validator-" + UUID.randomUUID());
        try (AdminClient admin = AdminClient.create(adminProperties)) {
            var descriptions = admin.describeTopics(List.of(options.sourceTopic(), options.targetTopic()))
                    .allTopicNames()
                    .get(options.timeout().toMillis(), TimeUnit.MILLISECONDS);
            int sourcePartitions = descriptions.get(options.sourceTopic()).partitions().size();
            int targetPartitions = descriptions.get(options.targetTopic()).partitions().size();
            if (sourcePartitions != targetPartitions) {
                throw new IllegalStateException("Source DLQ and target topic have different partition counts");
            }
            if (options.partition() >= sourcePartitions) {
                throw new IllegalArgumentException("Selected partition does not exist");
            }
        }
    }

    private static Properties kafkaProperties(
            DlqReplayOptions options,
            Map<String, String> environment
    ) throws IOException {
        Properties properties = new Properties();
        if (options.propertiesFile() != null) {
            try (var input = Files.newInputStream(options.propertiesFile())) {
                properties.load(input);
            }
        }
        copyEnvironment(properties, environment, "KAFKA_BOOTSTRAP_SERVERS",
                CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG);
        copyEnvironment(properties, environment, "KAFKA_SECURITY_PROTOCOL",
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG);
        copyEnvironment(properties, environment, "KAFKA_SASL_MECHANISM", SaslConfigs.SASL_MECHANISM);
        copyEnvironment(properties, environment, "KAFKA_SASL_JAAS_CONFIG", SaslConfigs.SASL_JAAS_CONFIG);
        copyEnvironment(properties, environment, "KAFKA_SSL_TRUSTSTORE_LOCATION", SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG);
        copyEnvironment(properties, environment, "KAFKA_SSL_TRUSTSTORE_PASSWORD", SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG);
        if (!properties.containsKey(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG)) {
            throw new IllegalArgumentException(
                    "Kafka bootstrap servers must be set in KAFKA_BOOTSTRAP_SERVERS or the properties file");
        }
        return properties;
    }

    private static void copyEnvironment(
            Properties target,
            Map<String, String> environment,
            String environmentName,
            String propertyName
    ) {
        String value = environment.get(environmentName);
        if (!target.containsKey(propertyName) && value != null && !value.isBlank()) {
            target.setProperty(propertyName, value.trim());
        }
    }

    private static Properties copy(Properties source) {
        Properties copy = new Properties();
        copy.putAll(source);
        return copy;
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static String auditJson(
            String action,
            DlqReplayOptions options,
            DlqRecord record,
            String payloadSha256,
            ReplayResult result
    ) {
        return "{" +
                "\"timestamp\":" + json(Instant.now().toString()) +
                ",\"action\":" + json(action) +
                ",\"sourceTopic\":" + json(options.sourceTopic()) +
                ",\"sourcePartition\":" + options.partition() +
                ",\"sourceOffset\":" + options.offset() +
                ",\"sourceTimestamp\":" + record.timestamp() +
                ",\"targetTopic\":" + json(options.targetTopic()) +
                ",\"keySha256\":" + json(record.key() == null ? null : sha256(record.key())) +
                ",\"payloadSha256\":" + json(payloadSha256) +
                ",\"operator\":" + json(options.operator()) +
                ",\"reason\":" + json(options.reason()) +
                ",\"targetPartition\":" + (result == null ? "null" : result.partition()) +
                ",\"targetOffset\":" + (result == null ? "null" : result.offset()) +
                "}";
    }

    private static String json(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder(value.length() + 2).append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format("\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    private static boolean containsHelp(String[] args) {
        for (String argument : args) {
            if ("--help".equals(argument) || "-h".equals(argument)) {
                return true;
            }
        }
        return false;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    }

    private static String usage() {
        return """
                OrderFlow DLQ replay

                Dry-run:
                  --topic <orderflow.*.dlq> --partition <n> --offset <n>
                  [--properties <file>] [--timeout-seconds <1..60>]

                Replay:
                  add --execute --expected-sha256 <sha256> --operator <name> --reason <text>

                KAFKA_BOOTSTRAP_SERVERS is required unless bootstrap.servers is present
                in the supplied Kafka properties file.
                """;
    }

    private record DlqRecord(byte[] key, byte[] value, long timestamp) {
    }

    private record ReplayResult(int partition, long offset) {
    }
}

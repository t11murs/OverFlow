package com.orderflow.tools.dlq;

import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

record DlqReplayOptions(
        String sourceTopic,
        int partition,
        long offset,
        boolean execute,
        String expectedSha256,
        String operator,
        String reason,
        Path propertiesFile,
        Duration timeout
) {
    private static final String DLQ_SUFFIX = ".dlq";
    private static final Pattern TOPIC_PATTERN = Pattern.compile("orderflow\\.[a-zA-Z0-9._-]+\\.dlq");
    private static final Pattern SHA_256_PATTERN = Pattern.compile("[a-fA-F0-9]{64}");
    private static final Set<String> VALUE_OPTIONS = Set.of(
            "topic", "partition", "offset", "expected-sha256", "operator", "reason",
            "properties", "timeout-seconds"
    );

    static DlqReplayOptions parse(String[] args) {
        Map<String, String> values = new HashMap<>();
        boolean execute = false;

        for (int index = 0; index < args.length; index++) {
            String argument = args[index];
            if ("--execute".equals(argument)) {
                if (execute) {
                    throw new IllegalArgumentException("Option --execute is duplicated");
                }
                execute = true;
                continue;
            }
            if (!argument.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected argument: " + argument);
            }
            String option = argument.substring(2);
            if (!VALUE_OPTIONS.contains(option)) {
                throw new IllegalArgumentException("Unknown option: " + argument);
            }
            if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
                throw new IllegalArgumentException("Option requires a value: " + argument);
            }
            if (values.putIfAbsent(option, args[++index]) != null) {
                throw new IllegalArgumentException("Option is duplicated: " + argument);
            }
        }

        String topic = required(values, "topic");
        if (!TOPIC_PATTERN.matcher(topic).matches()) {
            throw new IllegalArgumentException("Source topic must be an OrderFlow topic ending with .dlq");
        }
        String targetTopic = topic.substring(0, topic.length() - DLQ_SUFFIX.length());
        if (targetTopic.endsWith(DLQ_SUFFIX)) {
            throw new IllegalArgumentException("Nested DLQ topics cannot be replayed");
        }
        int partition = parseInt(required(values, "partition"), "partition", 0, Integer.MAX_VALUE);
        long offset = parseLong(required(values, "offset"), "offset", 0, Long.MAX_VALUE);
        int timeoutSeconds = parseInt(values.getOrDefault("timeout-seconds", "10"),
                "timeout-seconds", 1, 60);
        String expectedSha256 = blankToNull(values.get("expected-sha256"));
        String operator = blankToNull(values.get("operator"));
        String reason = blankToNull(values.get("reason"));

        if (expectedSha256 != null && !SHA_256_PATTERN.matcher(expectedSha256).matches()) {
            throw new IllegalArgumentException("Expected SHA-256 must contain 64 hexadecimal characters");
        }
        if (execute) {
            if (expectedSha256 == null || operator == null || reason == null) {
                throw new IllegalArgumentException(
                        "Execution requires --expected-sha256, --operator and --reason");
            }
            if (operator.length() > 128 || reason.length() < 10 || reason.length() > 500) {
                throw new IllegalArgumentException("Operator or replay reason has an invalid length");
            }
        }

        String properties = blankToNull(values.get("properties"));
        return new DlqReplayOptions(
                topic,
                partition,
                offset,
                execute,
                expectedSha256 == null ? null : expectedSha256.toLowerCase(),
                operator,
                reason,
                properties == null ? null : Path.of(properties),
                Duration.ofSeconds(timeoutSeconds)
        );
    }

    String targetTopic() {
        return sourceTopic.substring(0, sourceTopic.length() - DLQ_SUFFIX.length());
    }

    private static String required(Map<String, String> values, String name) {
        String value = blankToNull(values.get(name));
        if (value == null) {
            throw new IllegalArgumentException("Missing required option --" + name);
        }
        return value;
    }

    private static int parseInt(String value, String name, int minimum, int maximum) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < minimum || parsed > maximum) {
                throw new IllegalArgumentException("Option --" + name + " is outside the allowed range");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Option --" + name + " must be an integer", exception);
        }
    }

    private static long parseLong(String value, String name, long minimum, long maximum) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < minimum || parsed > maximum) {
                throw new IllegalArgumentException("Option --" + name + " is outside the allowed range");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Option --" + name + " must be an integer", exception);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

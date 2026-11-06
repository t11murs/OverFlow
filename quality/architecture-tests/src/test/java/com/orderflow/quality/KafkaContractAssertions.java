package com.orderflow.quality;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import static org.assertj.core.api.Assertions.assertThat;

public final class KafkaContractAssertions {
    private static final SchemaRegistry SCHEMAS =
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12, builder -> builder
                    .schemaIdResolvers(resolvers -> resolvers.mapPrefix(
                            "https://orderflow.local/contracts/kafka/", "classpath:kafka/")));

    private KafkaContractAssertions() {
    }

    public static void assertValid(String schemaName, String json) {
        var schema = SCHEMAS.getSchema(SchemaLocation.of(
                "https://orderflow.local/contracts/kafka/" + schemaName));
        var errors = schema.validate(json, InputFormat.JSON, context ->
                context.executionConfig(config -> config.formatAssertionsEnabled(true)));
        assertThat(errors)
                .as("Фактическое сообщение producer должно соответствовать %s", schemaName)
                .isEmpty();
    }
}

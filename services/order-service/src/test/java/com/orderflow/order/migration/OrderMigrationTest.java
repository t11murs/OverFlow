package com.orderflow.order.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Testcontainers(disabledWithoutDocker = true)
class OrderMigrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine")
            .withStartupTimeout(java.time.Duration.ofMinutes(3));

    @Test
    void migrationCreatesOrderTables() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection connection = POSTGRES.createConnection("")) {
            assertEquals(Set.of(
                    "customer_order",
                    "seller_order",
                    "order_item",
                    "fulfillment_group",
                    "fulfillment_item",
                    "order_process",
                    "integration_outbox",
                    "integration_inbox"), applicationTables(connection.getMetaData()));
        }
    }

    private static Set<String> applicationTables(DatabaseMetaData metadata) throws SQLException {
        Set<String> tables = new HashSet<>();
        try (ResultSet result = metadata.getTables(null, "public", "%", new String[]{"TABLE"})) {
            while (result.next()) {
                String name = result.getString("TABLE_NAME");
                if (!name.startsWith("flyway_")) {
                    tables.add(name);
                }
            }
        }
        return tables;
    }
}


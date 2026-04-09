package com.kim.fraudengine;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationFilesTest {

    @Test
    void shouldDefineProcessedTransactionsSchema() throws IOException {
        String migration = read("db/migration/V1__create_processed_transactions.sql");

        assertThat(migration)
                .contains("create table processed_transactions")
                .contains("transaction_id uuid primary key")
                .contains("customer_id varchar(255) not null")
                .contains("occurred_at timestamp with time zone not null");
    }

    @Test
    void shouldDefineFraudAlertsSchema() throws IOException {
        String migration = read("db/migration/V2__create_fraud_alerts.sql");

        assertThat(migration)
                .contains("create table fraud_alerts")
                .contains("transaction_id uuid not null")
                .contains("triggered_rules jsonb")
                .contains("constraint uk_fraud_alerts_transaction_id unique (transaction_id)");
    }

    private String read(String path) throws IOException {
        try (var input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input).as("Missing migration resource: %s", path).isNotNull();
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replaceAll("\\s+", " ");
        }
    }
}

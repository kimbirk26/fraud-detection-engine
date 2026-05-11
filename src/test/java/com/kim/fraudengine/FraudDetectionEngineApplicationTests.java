package com.kim.fraudengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.kim.fraudengine.application.FraudDetectionService;
import com.kim.fraudengine.domain.port.outbound.AlertRepository;
import com.kim.fraudengine.domain.port.outbound.RuleConfigurationProvider;
import com.kim.fraudengine.domain.port.outbound.TransactionHistoryRepository;
import com.kim.fraudengine.infrastructure.config.RuleConfig;
import java.util.List;
import com.kim.fraudengine.infrastructure.observability.FraudMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

@SpringBootTest(
        classes = {
            FraudDetectionEngineApplicationTests.TestConfig.class,
            FraudDetectionService.class,
            RuleConfig.class
        })
class FraudDetectionEngineApplicationTests {

    @Autowired private FraudDetectionService service;

    @Test
    void contextLoads() {
        assertThat(service).isNotNull();
    }

    @TestConfiguration
    static class TestConfig {

        @Bean
        AlertRepository alertRepository() {
            return Mockito.mock(AlertRepository.class);
        }

        @Bean
        TransactionHistoryRepository transactionHistoryRepository() {
            return Mockito.mock(TransactionHistoryRepository.class);
        }

        @Bean
        TransactionOperations transactionOperations() {
            return new TransactionOperations() {
                @Override
                public <T> T execute(TransactionCallback<T> action) {
                    return action.doInTransaction(new SimpleTransactionStatus());
                }
            };
        }

        @Bean
        FraudMetrics fraudMetrics() {
            return new FraudMetrics(new SimpleMeterRegistry());
        }

        @Bean
        RuleConfigurationProvider ruleConfigurationProvider() {
            return List::of;
        }
    }
}

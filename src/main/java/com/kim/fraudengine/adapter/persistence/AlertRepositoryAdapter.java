package com.kim.fraudengine.adapter.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kim.fraudengine.adapter.persistence.entity.AlertEntity;
import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.FraudAlert;
import com.kim.fraudengine.domain.model.RuleResult;
import com.kim.fraudengine.domain.model.Severity;
import com.kim.fraudengine.domain.port.outbound.AlertRepository;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AlertRepositoryAdapter implements AlertRepository {

    private final AlertJpaRepository jpaRepository;
    private final ObjectMapper objectMapper;

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP2",
            justification =
                    "Spring-managed singleton - effectively immutable after context initialization")
    public AlertRepositoryAdapter(AlertJpaRepository jpaRepository, ObjectMapper objectMapper) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public FraudAlert save(FraudAlert alert) {
        AlertEntity entity = toEntity(alert);
        AlertEntity saved = jpaRepository.saveAndFlush(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<FraudAlert> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<FraudAlert> findByTransactionId(UUID transactionId) {
        return jpaRepository.findByTransactionId(transactionId).map(this::toDomain);
    }

    @Override
    public List<FraudAlert> findByCustomerId(String customerId) {
        return jpaRepository.findByCustomerId(customerId).stream().map(this::toDomain).toList();
    }

    @Override
    public List<FraudAlert> findByStatus(AlertStatus status) {
        return jpaRepository.findByStatus(status).stream().map(this::toDomain).toList();
    }

    @Override
    public List<FraudAlert> findBySeverity(Severity severity) {
        return jpaRepository.findByHighestSeverity(severity).stream().map(this::toDomain).toList();
    }

    private AlertEntity toEntity(FraudAlert alert) {
        try {
            String json = objectMapper.writeValueAsString(alert.triggeredRules());
            return new AlertEntity(
                    alert.id(),
                    alert.transactionId(),
                    alert.customerId(),
                    json,
                    alert.highestSeverity(),
                    alert.status(),
                    alert.createdAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise triggered rules", e);
        }
    }

    private FraudAlert toDomain(AlertEntity entity) {
        try {
            List<RuleResult> rules =
                    objectMapper.readValue(
                            entity.getTriggeredRulesJson(),
                            objectMapper
                                    .getTypeFactory()
                                    .constructCollectionType(List.class, RuleResult.class));
            return new FraudAlert(
                    entity.getId(),
                    entity.getTransactionId(),
                    entity.getCustomerId(),
                    rules,
                    entity.getHighestSeverity(),
                    entity.getStatus(),
                    entity.getCreatedAt());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise triggered rules", e);
        }
    }
}

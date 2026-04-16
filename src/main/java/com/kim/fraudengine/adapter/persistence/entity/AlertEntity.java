package com.kim.fraudengine.adapter.persistence.entity;

import com.kim.fraudengine.domain.model.AlertStatus;
import com.kim.fraudengine.domain.model.Severity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "fraud_alerts",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_fraud_alerts_transaction_id",
                        columnNames = "transaction_id"))
public class AlertEntity {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "transaction_id", nullable = false, columnDefinition = "uuid")
    private UUID transactionId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "triggered_rules", columnDefinition = "jsonb")
    private String triggeredRulesJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "highest_severity", nullable = false)
    private Severity highestSeverity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public AlertEntity(
            UUID id,
            UUID transactionId,
            String customerId,
            String triggeredRulesJson,
            Severity highestSeverity,
            AlertStatus status,
            Instant createdAt) {
        this.id = id;
        this.transactionId = transactionId;
        this.customerId = customerId;
        this.triggeredRulesJson = triggeredRulesJson;
        this.highestSeverity = highestSeverity;
        this.status = status;
        this.createdAt = createdAt;
    }
}

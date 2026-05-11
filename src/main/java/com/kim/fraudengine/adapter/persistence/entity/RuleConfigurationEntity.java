package com.kim.fraudengine.adapter.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "rule_configurations")
public class RuleConfigurationEntity {

    @Id
    @Column(name = "rule_name", length = 50)
    private String ruleName;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private int score;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private String parameters;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public RuleConfigurationEntity(
            String ruleName, boolean enabled, int score, String parameters, Instant updatedAt) {
        this.ruleName = ruleName;
        this.enabled = enabled;
        this.score = score;
        this.parameters = parameters;
        this.updatedAt = updatedAt;
    }
}

package com.kim.fraudengine.adapter.persistence;

import com.kim.fraudengine.adapter.persistence.entity.RuleConfigurationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleConfigurationJpaRepository
        extends JpaRepository<RuleConfigurationEntity, String> {}

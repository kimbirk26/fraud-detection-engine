CREATE TABLE rule_configurations (
    rule_name   VARCHAR(50) PRIMARY KEY,
    enabled     BOOLEAN NOT NULL DEFAULT true,
    score       INTEGER NOT NULL,
    parameters  JSONB NOT NULL DEFAULT '{}',
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO rule_configurations (rule_name, enabled, score, parameters) VALUES
('AMOUNT_THRESHOLD', true, 40, '{"highThreshold":"50000.00","mediumThreshold":"10000.00","highScore":"40","mediumScore":"20"}'),
('BLACKLIST_MATCH',  true, 50, '{"merchantIds":"MERCH_FRAUD_001,MERCH_FRAUD_002,MERCH_FRAUD_003"}'),
('VELOCITY_CHECK',   true, 40, '{"maxTransactions":"5","windowMinutes":"10"}'),
('FOREIGN_COUNTRY',  true, 20, '{"homeCountryCode":"ZA","minimumAmount":"1000.00"}'),
('OUT_OF_HOURS',     true, 15, '{"start":"0","end":"5","timezone":"Africa/Johannesburg"}');

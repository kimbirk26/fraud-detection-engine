ALTER TABLE fraud_alerts ADD COLUMN correlation_group_id uuid;
CREATE INDEX idx_fraud_alerts_correlation_group ON fraud_alerts (correlation_group_id);
CREATE INDEX idx_fraud_alerts_customer_created ON fraud_alerts (customer_id, created_at DESC);

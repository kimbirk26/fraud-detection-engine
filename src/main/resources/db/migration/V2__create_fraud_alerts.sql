create table fraud_alerts
(
    id               uuid primary key,
    transaction_id   uuid                     not null,
    customer_id      varchar(255)             not null,
    triggered_rules  jsonb,
    highest_severity varchar(255)             not null,
    status           varchar(255)             not null,
    created_at       timestamp with time zone not null,
    constraint uk_fraud_alerts_transaction_id unique (transaction_id)
);

create index idx_fraud_alerts_customer_id
    on fraud_alerts (customer_id);

create index idx_fraud_alerts_status
    on fraud_alerts (status);

create index idx_fraud_alerts_highest_severity
    on fraud_alerts (highest_severity);

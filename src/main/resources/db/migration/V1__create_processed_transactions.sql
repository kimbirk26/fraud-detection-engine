create table processed_transactions
(
    transaction_id uuid primary key,
    customer_id    varchar(255)             not null,
    amount         numeric(19, 2)           not null,
    merchant_id    varchar(255)             not null,
    merchant_name  varchar(255)             not null,
    category       varchar(255)             not null,
    currency       varchar(3)               not null,
    country_code   varchar(2)               not null,
    occurred_at    timestamp with time zone not null
);

create index idx_processed_transactions_customer_occurred_at
    on processed_transactions (customer_id, occurred_at);

create table auth_users
(
    username                varchar(150) primary key,
    password_hash           varchar(255)             not null,
    customer_id             varchar(255),
    enabled                 boolean                  not null default true,
    account_non_locked      boolean                  not null default true,
    account_non_expired     boolean                  not null default true,
    credentials_non_expired boolean                  not null default true,
    created_at              timestamp with time zone not null default current_timestamp,
    updated_at              timestamp with time zone not null default current_timestamp
);

create table auth_user_authorities
(
    username  varchar(150) not null,
    authority varchar(100) not null,
    constraint pk_auth_user_authorities primary key (username, authority),
    constraint fk_auth_user_authorities_user
        foreign key (username) references auth_users (username) on delete cascade
);

create index idx_auth_users_customer_id
    on auth_users (customer_id);

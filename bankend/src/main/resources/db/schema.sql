create table if not exists novels (
    id bigserial primary key,
    title varchar(200) not null,
    description text,
    content text,
    deleted boolean not null default false,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_novels_deleted_created_at
    on novels (deleted, created_at desc);

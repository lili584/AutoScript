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

create table if not exists novel_chapters (
    id bigserial primary key,
    novel_id bigint not null,
    title varchar(200) not null,
    order_index integer not null,
    content text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_novel_chapters_novel_order
    on novel_chapters (novel_id, order_index);

create table if not exists novel_chunks (
    id bigserial primary key,
    novel_id bigint not null,
    chapter_id bigint not null,
    chapter_index integer not null,
    chunk_index integer not null,
    content text not null,
    context text,
    paragraph_start integer not null,
    paragraph_end integer not null,
    char_count integer not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_novel_chunks_novel_order
    on novel_chunks (novel_id, chapter_index, chunk_index);

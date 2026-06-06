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

create table if not exists script_generation_tasks (
    id bigserial primary key,
    novel_id bigint not null,
    status varchar(32) not null,
    total_chunks integer not null default 0,
    processed_chunks integer not null default 0,
    error_message text,
    started_at timestamp,
    completed_at timestamp,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_script_generation_tasks_novel_created
    on script_generation_tasks (novel_id, created_at desc);

create table if not exists script_scenes (
    id bigserial primary key,
    novel_id bigint not null,
    chapter_id bigint not null,
    chunk_id bigint not null,
    scene_id varchar(120) not null,
    title varchar(200) not null,
    location varchar(200),
    time_of_day varchar(100),
    summary text,
    characters_json text,
    beats_json text,
    source_refs_json text,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_script_scenes_novel_source
    on script_scenes (novel_id, chapter_id, chunk_id);

create table if not exists script_dialogues (
    id bigserial primary key,
    scene_db_id bigint not null,
    character_name varchar(120),
    text text not null,
    created_at timestamp not null default current_timestamp
);

create index if not exists idx_script_dialogues_scene
    on script_dialogues (scene_db_id);

create table if not exists script_chapter_states (
    id bigserial primary key,
    novel_id bigint not null,
    chapter_id bigint not null,
    chunk_id bigint not null,
    chapter_index integer not null,
    chunk_index integer not null,
    state_json text not null,
    created_at timestamp not null default current_timestamp,
    updated_at timestamp not null default current_timestamp
);

create index if not exists idx_script_chapter_states_novel_order
    on script_chapter_states (novel_id, chapter_index, chunk_index);

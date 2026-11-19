-- Esquema inicial do serviço de ficheiros.
-- Os ficheiros em si ficam no disco; aqui guardamos as aplicações registadas e os metadados.

create table fs_application (
    id                    uuid                     not null,
    code                  varchar(50)              not null,
    name                  varchar(150)             not null,
    api_key_hash          varchar(64)              not null,
    api_key_prefix        varchar(16)              not null,
    max_file_size_bytes   bigint,
    allowed_content_types varchar(2000),
    storage_quota_bytes   bigint,
    active                boolean                  not null,
    created_at            timestamp with time zone not null,
    updated_at            timestamp with time zone not null,
    constraint pk_fs_application primary key (id),
    constraint uq_fs_application_code unique (code)
);

create table fs_application_origin (
    application_id uuid         not null,
    origin         varchar(255) not null,
    constraint pk_fs_application_origin primary key (application_id, origin),
    constraint fk_fs_application_origin_app foreign key (application_id)
        references fs_application (id) on delete cascade
);

create index ix_fs_application_origin_origin on fs_application_origin (origin);

create table fs_file (
    id                uuid                     not null,
    application_id    uuid                     not null,
    original_filename varchar(255)             not null,
    storage_path      varchar(500)             not null,
    content_type      varchar(150)             not null,
    size_bytes        bigint                   not null,
    checksum_sha256   varchar(64)              not null,
    visibility        varchar(10)              not null,
    folder            varchar(100),
    uploaded_by       varchar(150),
    created_at        timestamp with time zone not null,
    deleted_at        timestamp with time zone,
    constraint pk_fs_file primary key (id),
    constraint fk_fs_file_app foreign key (application_id) references fs_application (id),
    constraint ck_fs_file_visibility check (visibility in ('PRIVATE', 'PUBLIC')),
    constraint ck_fs_file_size check (size_bytes >= 0)
);

create index ix_fs_file_app_created on fs_file (application_id, created_at desc);
create index ix_fs_file_app_folder on fs_file (application_id, folder);

create table fs_audit_event (
    id               uuid                     not null,
    event_type       varchar(40)              not null,
    application_code varchar(50),
    resource_id      varchar(100),
    details          varchar(250)             not null,
    occurred_at      timestamp with time zone not null,
    constraint pk_fs_audit_event primary key (id)
);

create index ix_fs_audit_event_occurred on fs_audit_event (occurred_at desc);

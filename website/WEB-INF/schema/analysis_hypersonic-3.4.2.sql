--
create table eula_record (
    id bigint generated by default as identity (start with 1),
    lsid varchar(255) not null,
    user_id varchar(255) not null,
    date_recorded timestamp default now not null,
    primary key (id),
    constraint user_id_fk foreign key (USER_ID) references GP_USER(USER_ID),
    unique (user_id, lsid)
);
create index idx_eula_record_lsid on eula_record (lsid);
create index idx_eula_record_user_id on eula_record (user_id); 

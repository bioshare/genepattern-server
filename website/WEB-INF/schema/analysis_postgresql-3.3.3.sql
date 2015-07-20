-- record of user upload files, generated by [hibernatetool] 
create table user_upload (
        id bigserial primary key,
        extension text,
        file_length decimal(19,0),
        kind text,
        last_modified timestamp,
        name text,
        num_parts bigint,
        num_parts_recd bigint,
        path text,
        user_id text,
        unique (user_id, path)
    );

create index idx_user_upload_user_id on user_upload (user_id);

-- for GenomeSpace integration, link GP user account to GS user account
create table GS_ACCOUNT (
    -- use the File.canonicalPath as the primary key 
    GP_USERID text PRIMARY KEY references GP_USER(USER_ID),
    -- owner of the file
    TOKEN text
);

-- improve performance by creating indexes on the ANALYSIS_JOB table
CREATE INDEX IDX_AJ_STATUS ON ANALYSIS_JOB(STATUS_ID);
CREATE INDEX IDX_AJ_PARENT ON ANALYSIS_JOB(PARENT);


-- for SGE integration
CREATE TABLE JOB_SGE
(
    GP_JOB_NO BIGINT NOT NULL,
    SGE_JOB_ID TEXT,
    SGE_SUBMIT_TIME TIMESTAMP,
    SGE_START_TIME TIMESTAMP,
    SGE_END_TIME TIMESTAMP,
    SGE_RETURN_CODE BIGINT,
    SGE_JOB_COMPLETION_STATUS TEXT,
    PRIMARY KEY (GP_JOB_NO)
);

CREATE INDEX IDX_SGE_JOB_ID on JOB_SGE (SGE_JOB_ID);

COMMIT;







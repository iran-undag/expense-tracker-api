CREATE TABLE demo_session (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    shared_account_id VARCHAR(64) NOT NULL,
    persistence_owner_id VARCHAR(96) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at DATETIMEOFFSET(6) NOT NULL,
    expires_at DATETIMEOFFSET(6) NOT NULL,
    used_actions INT NOT NULL,
    reserved_actions INT NOT NULL,
    resume_token_digest CHAR(64) NOT NULL,
    CONSTRAINT uk_demo_session_owner UNIQUE (persistence_owner_id),
    CONSTRAINT uk_demo_session_resume_digest UNIQUE (resume_token_digest),
    CONSTRAINT ck_demo_used_actions CHECK (used_actions BETWEEN 0 AND 20),
    CONSTRAINT ck_demo_reserved_actions CHECK (reserved_actions BETWEEN 0 AND 20),
    CONSTRAINT ck_demo_total_actions CHECK (used_actions + reserved_actions <= 20)
);

CREATE INDEX ix_demo_session_expires_at ON demo_session (expires_at);

CREATE TABLE demo_access_token (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    demo_session_id UNIQUEIDENTIFIER NOT NULL,
    token_digest CHAR(64) NOT NULL,
    created_at DATETIMEOFFSET(6) NOT NULL,
    expires_at DATETIMEOFFSET(6) NOT NULL,
    CONSTRAINT uk_demo_access_token_digest UNIQUE (token_digest),
    CONSTRAINT fk_demo_access_token_session FOREIGN KEY (demo_session_id)
        REFERENCES demo_session(id) ON DELETE CASCADE
);

CREATE INDEX ix_demo_access_token_expires_at ON demo_access_token (expires_at);

CREATE TABLE demo_quota_reservation (
    id UNIQUEIDENTIFIER NOT NULL PRIMARY KEY,
    demo_session_id UNIQUEIDENTIFIER NOT NULL,
    cost INT NOT NULL,
    state VARCHAR(24) NOT NULL,
    created_at DATETIMEOFFSET(6) NOT NULL,
    expires_at DATETIMEOFFSET(6) NOT NULL,
    CONSTRAINT ck_demo_reservation_cost CHECK (cost BETWEEN 1 AND 20),
    CONSTRAINT fk_demo_quota_reservation_session FOREIGN KEY (demo_session_id)
        REFERENCES demo_session(id) ON DELETE CASCADE
);

CREATE INDEX ix_demo_quota_reservation_expires_at
    ON demo_quota_reservation (expires_at);

CREATE TABLE demo_seed_state (
    id TINYINT NOT NULL PRIMARY KEY,
    template_version INT NOT NULL,
    anchor_month DATE NOT NULL,
    refreshed_at DATETIMEOFFSET(6) NOT NULL,
    CONSTRAINT ck_demo_seed_state_singleton CHECK (id = 1)
);

CREATE TABLE demo_session_attempt (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    ip_digest CHAR(64) NOT NULL,
    attempted_at DATETIMEOFFSET(6) NOT NULL
);

CREATE INDEX ix_demo_session_attempt_attempted_at
    ON demo_session_attempt (attempted_at);

ALTER TABLE expense ADD
    demo_session_id UNIQUEIDENTIFIER NULL,
    is_demo_seed BIT NOT NULL CONSTRAINT df_expense_is_demo_seed DEFAULT 0 WITH VALUES;

ALTER TABLE expense ADD CONSTRAINT fk_expense_demo_session
    FOREIGN KEY (demo_session_id) REFERENCES demo_session(id);

CREATE INDEX ix_expense_demo_scope ON expense (is_demo_seed, demo_session_id);

ALTER TABLE budget ADD
    demo_session_id UNIQUEIDENTIFIER NULL,
    is_demo_seed BIT NOT NULL CONSTRAINT df_budget_is_demo_seed DEFAULT 0 WITH VALUES;

ALTER TABLE budget ADD CONSTRAINT fk_budget_demo_session
    FOREIGN KEY (demo_session_id) REFERENCES demo_session(id);

CREATE INDEX ix_budget_demo_scope ON budget (is_demo_seed, demo_session_id);

ALTER TABLE expense_category ADD
    demo_session_id UNIQUEIDENTIFIER NULL,
    is_demo_seed BIT NOT NULL CONSTRAINT df_expense_category_is_demo_seed DEFAULT 0 WITH VALUES;

ALTER TABLE expense_category ADD CONSTRAINT fk_expense_category_demo_session
    FOREIGN KEY (demo_session_id) REFERENCES demo_session(id);

CREATE INDEX ix_expense_category_demo_scope
    ON expense_category (is_demo_seed, demo_session_id);

ALTER TABLE recurring_expense ADD
    demo_session_id UNIQUEIDENTIFIER NULL,
    is_demo_seed BIT NOT NULL CONSTRAINT df_recurring_expense_is_demo_seed DEFAULT 0 WITH VALUES;

ALTER TABLE recurring_expense ADD CONSTRAINT fk_recurring_expense_demo_session
    FOREIGN KEY (demo_session_id) REFERENCES demo_session(id);

CREATE INDEX ix_recurring_expense_demo_scope
    ON recurring_expense (is_demo_seed, demo_session_id);

ALTER TABLE chat_identity_mapping ADD demo_session_id UNIQUEIDENTIFIER NULL;

ALTER TABLE chat_identity_mapping ADD CONSTRAINT fk_chat_identity_demo_session
    FOREIGN KEY (demo_session_id) REFERENCES demo_session(id);

CREATE INDEX ix_chat_identity_demo_session
    ON chat_identity_mapping (demo_session_id);

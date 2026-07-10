CREATE TABLE chat_identity_mapping (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    direct_line_user_id VARCHAR(128) NOT NULL,
    conversation_id VARCHAR(255) NOT NULL,
    userid VARCHAR(255) NOT NULL,
    expires_at DATETIME2 NOT NULL,
    created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT uk_chat_identity_dl_user UNIQUE (direct_line_user_id),
    CONSTRAINT uk_chat_identity_conversation UNIQUE (conversation_id)
);

CREATE INDEX ix_chat_identity_expires_at
    ON chat_identity_mapping (expires_at);

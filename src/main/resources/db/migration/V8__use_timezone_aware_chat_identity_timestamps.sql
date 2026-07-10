DROP INDEX ix_chat_identity_expires_at
    ON chat_identity_mapping;

DECLARE @created_at_default sysname;
DECLARE @drop_default_sql nvarchar(max);

SELECT @created_at_default = dc.name
FROM sys.default_constraints dc
JOIN sys.columns column_definition
    ON column_definition.default_object_id = dc.object_id
WHERE dc.parent_object_id = OBJECT_ID(N'dbo.chat_identity_mapping')
  AND column_definition.name = N'created_at';

IF @created_at_default IS NOT NULL
BEGIN
    SET @drop_default_sql =
        N'ALTER TABLE dbo.chat_identity_mapping DROP CONSTRAINT '
        + QUOTENAME(@created_at_default);
    EXEC sys.sp_executesql @drop_default_sql;
END;

ALTER TABLE chat_identity_mapping
    ALTER COLUMN expires_at DATETIMEOFFSET(6) NOT NULL;

ALTER TABLE chat_identity_mapping
    ALTER COLUMN created_at DATETIMEOFFSET(6) NOT NULL;

ALTER TABLE chat_identity_mapping
    ADD CONSTRAINT df_chat_identity_created_at
        DEFAULT SYSDATETIMEOFFSET() FOR created_at;

CREATE INDEX ix_chat_identity_expires_at
    ON chat_identity_mapping (expires_at);

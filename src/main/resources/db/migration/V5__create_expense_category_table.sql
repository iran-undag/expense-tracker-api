CREATE TABLE expense_category (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    userid VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    color VARCHAR(32),
    icon VARCHAR(64),
    system_default BIT NOT NULL DEFAULT 0,
    active BIT NOT NULL DEFAULT 1,
    CONSTRAINT uk_expense_category_user_name UNIQUE (userid, name)
);

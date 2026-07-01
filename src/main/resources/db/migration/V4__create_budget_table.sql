CREATE TABLE budget (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    userid VARCHAR(255) NOT NULL,
    category VARCHAR(255) NOT NULL,
    budget_year INT NOT NULL,
    budget_month INT NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    CONSTRAINT uk_budget_user_month_category UNIQUE (userid, budget_year, budget_month, category)
);

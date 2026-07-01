CREATE TABLE recurring_expense (
    id BIGINT IDENTITY PRIMARY KEY,
    userid NVARCHAR(255) NOT NULL,
    description NVARCHAR(255),
    amount DECIMAL(19, 2) NOT NULL,
    category NVARCHAR(255),
    frequency NVARCHAR(20) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,
    next_run_date DATE NOT NULL,
    active BIT NOT NULL DEFAULT 1
);

CREATE TABLE recurring_expense_occurrence (
    id BIGINT IDENTITY PRIMARY KEY,
    recurring_expense_id BIGINT NOT NULL,
    userid NVARCHAR(255) NOT NULL,
    occurrence_date DATE NOT NULL,
    expense_id BIGINT NOT NULL,
    CONSTRAINT uk_recurring_occurrence_rule_date UNIQUE (recurring_expense_id, occurrence_date)
);

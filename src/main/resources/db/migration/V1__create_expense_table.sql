CREATE TABLE expense (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    description VARCHAR(255),
    amount DECIMAL(19, 2) NOT NULL,
    date DATE,
    category VARCHAR(255)
);

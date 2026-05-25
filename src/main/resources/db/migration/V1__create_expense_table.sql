CREATE TABLE expense (
    id BIGSERIAL PRIMARY KEY,
    description VARCHAR(255),
    amount NUMERIC(19, 2) NOT NULL,
    date DATE,
    category VARCHAR(255)
);

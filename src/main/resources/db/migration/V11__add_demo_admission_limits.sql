DROP TABLE demo_session_attempt;

CREATE TABLE demo_session_admission (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    admitted_at DATETIMEOFFSET(6) NOT NULL
);

CREATE INDEX ix_demo_session_admission_admitted_at
    ON demo_session_admission (admitted_at);

DELETE FROM demo_quota_reservation;

UPDATE demo_session
SET reserved_actions = 0,
    used_actions = CASE WHEN used_actions > 10 THEN 10 ELSE used_actions END;

ALTER TABLE demo_session DROP CONSTRAINT ck_demo_used_actions;
ALTER TABLE demo_session DROP CONSTRAINT ck_demo_reserved_actions;
ALTER TABLE demo_session DROP CONSTRAINT ck_demo_total_actions;
ALTER TABLE demo_quota_reservation DROP CONSTRAINT ck_demo_reservation_cost;

ALTER TABLE demo_session ADD CONSTRAINT ck_demo_used_actions
    CHECK (used_actions BETWEEN 0 AND 10);

ALTER TABLE demo_session ADD CONSTRAINT ck_demo_reserved_actions
    CHECK (reserved_actions BETWEEN 0 AND 10);

ALTER TABLE demo_session ADD CONSTRAINT ck_demo_total_actions
    CHECK (used_actions + reserved_actions <= 10);

ALTER TABLE demo_quota_reservation ADD CONSTRAINT ck_demo_reservation_cost
    CHECK (cost BETWEEN 1 AND 10);

IF COL_LENGTH('expense', 'username') IS NOT NULL
BEGIN
    ALTER TABLE expense DROP COLUMN username;
END;

-- V1 created these as plain TIMESTAMP. The design doc mandates TIMESTAMPTZ for every timestamp
-- column, and Hibernate maps Instant to timestamptz — existing values were written as UTC.
ALTER TABLE users
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';

-- Give the unique index a stable name so the persistence adapter can recognise the violation.
ALTER TABLE users RENAME CONSTRAINT users_email_key TO uq_users_email;

-- Logins now look emails up in normalized form; bring existing rows in line so they stay reachable.
-- If two rows differ only by casing this fails loudly, which is the right outcome — it needs a human.
UPDATE users SET email = lower(trim(email)) WHERE email <> lower(trim(email));

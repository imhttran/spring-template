-- The whole schema, in one file: applied by Flyway on first boot and recorded in
-- flyway_schema_history. Every statement is IF NOT EXISTS, so it is also a no-op
-- against a database that already has these tables.
--
-- Schema changes from here: add V2__*.sql, V3__*.sql, … — the filename is the
-- registration, there is no list to keep in sync.

CREATE TABLE IF NOT EXISTS users (
  id                  SERIAL PRIMARY KEY,
  email               TEXT NOT NULL UNIQUE,
  password            TEXT NOT NULL,
  role                TEXT NOT NULL DEFAULT 'client',
  email_verified      BOOLEAN NOT NULL DEFAULT false,
  must_change_password BOOLEAN NOT NULL DEFAULT false,
  verification_token  TEXT UNIQUE,
  reset_token         TEXT UNIQUE,
  reset_token_expiry  TIMESTAMPTZ,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One-time registration details. A missing row (not a boolean flag, unlike
-- must_change_password) is what gates a user into the completion form — the
-- data itself is the "is this done" signal, so there's nothing to keep in sync.
CREATE TABLE IF NOT EXISTS user_profiles (
  id                       SERIAL PRIMARY KEY,
  user_id                  INTEGER NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
  first_name               TEXT NOT NULL,
  last_name                TEXT NOT NULL,
  address                  TEXT NOT NULL,
  address2                 TEXT,
  state                    TEXT NOT NULL,
  zip                      TEXT NOT NULL,
  country                  TEXT NOT NULL DEFAULT 'US',
  phone                    TEXT NOT NULL,
  communication_preference TEXT NOT NULL DEFAULT 'email',
  linkedin                 TEXT,
  github                   TEXT,
  alt_email                TEXT
);

-- Outbound mail, drained by the @Scheduled worker. "to" is a reserved word, so
-- it is quoted here and in every query that touches it.
CREATE TABLE IF NOT EXISTS email_queue (
  id         SERIAL PRIMARY KEY,
  "to"       TEXT NOT NULL,
  subject    TEXT NOT NULL,
  body       TEXT NOT NULL,
  status     TEXT NOT NULL DEFAULT 'pending',
  attempts   INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  sent_at    TIMESTAMPTZ
);

-- 2FA: trusted devices that skip the login code, and the pending codes
-- themselves.
CREATE TABLE IF NOT EXISTS user_devices (
  id         SERIAL PRIMARY KEY,
  user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  device_id  TEXT NOT NULL UNIQUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS login_codes (
  id         SERIAL PRIMARY KEY,
  user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token      TEXT NOT NULL UNIQUE,
  code       TEXT NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  used       BOOLEAN NOT NULL DEFAULT false,
  attempts   INTEGER NOT NULL DEFAULT 0,
  resends    INTEGER NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

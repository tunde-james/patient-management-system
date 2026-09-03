ALTER TABLE users DROP COLUMN locked_since,
  ADD COLUMN locked_until TIMESTAMPTZ,
  ADD COLUMN failed_login_count INT NOT NULL DEFAULT 0,
  ADD COLUMN failed_window_started_at TIMESTAMPTZ;
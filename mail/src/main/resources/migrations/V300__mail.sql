CREATE TABLE mail_account (
  key BIGINT PRIMARY KEY,
  name TEXT NOT NULL,
  email TEXT NOT NULL UNIQUE,
  settings BYTEA NOT NULL
);

CREATE TABLE mail_data (
  id BIGINT PRIMARY KEY,
  raw_body TEXT NOT NULL,
  account_id BIGINT NOT NULL,
  internal_date BIGINT NOT NULL,
  timestamp BIGINT NOT NULL
);

-- Indexes
CREATE INDEX idx_mail_account_email ON mail_account(email);

CREATE INDEX idx_mail_data_account ON mail_data(account_id);
CREATE INDEX idx_mail_data_internal_date ON mail_data(internal_date);
CREATE INDEX idx_mail_data_job_id ON mail_data(job_id);

--  Event log

CREATE TABLE IF NOT EXISTS mail_log (
  version BIGINT NOT NULL PRIMARY KEY,
  aggregate_id BIGINT NOT NULL,
  discriminator TEXT NOT NULL,
  namespace INT NOT NULL,
  reference BIGINT,
  parent_id BIGINT,
  grand_parent_id BIGINT,
  props JSONB NOT NULL DEFAULT '{}'::jsonb,
  payload BYTEA NOT NULL
);

CREATE INDEX idx_mail_log_discriminator ON mail_log (discriminator);
CREATE INDEX idx_mail_log_aggregate_id ON mail_log (aggregate_id);
CREATE INDEX idx_mail_log_namespace ON mail_log (namespace);
CREATE INDEX idx_mail_log_reference ON mail_log (reference);
CREATE INDEX idx_mail_log_parent_id ON mail_log (parent_id);
CREATE INDEX idx_mail_log_grand_parent_id ON mail_log (grand_parent_id);

CREATE INDEX idx_mail_log_props_all_keys ON mail_log USING GIN (props);


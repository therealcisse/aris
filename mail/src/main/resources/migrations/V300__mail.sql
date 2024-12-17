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


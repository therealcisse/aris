-- Drop tables if they exist
DROP TABLE IF EXISTS migrations CASCADE;

CREATE TABLE IF NOT EXISTS migrations (
  id BIGINT NOT NULL,
  state BYTEA NOT NULL,
  timestamp BIGINT NOT NULL,
  PRIMARY KEY (id)
);

-- Index for quick lookup of migrations by timestamp
CREATE INDEX idx_migrations_timestamp ON migrations (timestamp);

--  Event log

CREATE TABLE IF NOT EXISTS migration_log (
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

CREATE INDEX idx_migration_log_discriminator ON migration_log (discriminator);
CREATE INDEX idx_migration_log_aggregate_id ON migration_log (aggregate_id);
CREATE INDEX idx_migration_log_namespace ON migration_log (namespace);
CREATE INDEX idx_migration_log_reference ON migration_log (reference);
CREATE INDEX idx_migration_log_parent_id ON migration_log (parent_id);
CREATE INDEX idx_migration_log_grand_parent_id ON migration_log (grand_parent_id);

CREATE INDEX idx_migration_log_props_all_keys ON migration_log USING GIN (props);


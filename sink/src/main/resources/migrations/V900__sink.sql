--  Event log

CREATE TABLE IF NOT EXISTS sink_log (
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

CREATE INDEX idx_sink_log_discriminator ON sink_log (discriminator);
CREATE INDEX idx_sink_log_aggregate_id ON sink_log (aggregate_id);
CREATE INDEX idx_sink_log_namespace ON sink_log (namespace);
CREATE INDEX idx_sink_log_reference ON sink_log (reference);
CREATE INDEX idx_sink_log_parent_id ON sink_log (parent_id);
CREATE INDEX idx_sink_log_grand_parent_id ON sink_log (grand_parent_id);

CREATE INDEX idx_sink_log_props_all_keys ON sink_log USING GIN (props);


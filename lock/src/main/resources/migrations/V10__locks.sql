CREATE TABLE IF NOT EXISTS locks (
    lock_key TEXT PRIMARY KEY,
    timestamp BIGINT NOT NULL
);

CREATE OR REPLACE FUNCTION acquire_lock(in_lock_key TEXT, ts BIGINT) RETURNS BOOLEAN AS $$
DECLARE
    n INT := 0;
BEGIN
    LOCK locks IN EXCLUSIVE MODE;

    INSERT INTO locks (lock_key, timestamp)
    VALUES (in_lock_key, ts)
    ON CONFLICT (lock_key) DO NOTHING;

    GET DIAGNOSTICS n = ROW_COUNT;

    RETURN n > 0;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION release_lock(in_lock_key TEXT) RETURNS BOOLEAN AS $$
DECLARE
    n INT := 0;
BEGIN
    LOCK locks IN EXCLUSIVE MODE;

    DELETE FROM locks
    WHERE lock_key = in_lock_key;

    GET DIAGNOSTICS n = ROW_COUNT;

    RETURN n > 0;
END;
$$ LANGUAGE plpgsql;

CREATE INDEX IF NOT EXISTS idx_locks_timestamp ON locks (timestamp);

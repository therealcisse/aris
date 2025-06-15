CREATE TABLE IF NOT EXISTS tenants (
  id INT PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NOT NULL,
  created BIGINT NOT NULL,
  status TEXT NOT NULL
);

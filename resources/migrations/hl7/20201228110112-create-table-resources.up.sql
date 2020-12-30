CREATE TABLE IF NOT EXISTS resources (
  id UUID DEFAULT uuid_generate_v4(),
  PRIMARY KEY (id),
  data JSONB,
  created_at TIMESTAMP NOT NULL DEFAULT current_timestamp,
  updated_at TIMESTAMP NOT NULL DEFAULT current_timestamp
);

--;;

CREATE INDEX IF NOT EXISTS idx_resources_data ON resources USING gin (data);

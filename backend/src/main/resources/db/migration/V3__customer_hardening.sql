ALTER TABLE customer
  ADD COLUMN email VARCHAR(254),
  ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  ADD CONSTRAINT ck_customer_email CHECK (email ~ '^[^@\s]+@[^@\s]+$');

CREATE UNIQUE INDEX ux_customer_email ON customer (lower(email));

UPDATE customer SET email = 'amina.khan@example.test' WHERE public_id = 'CUS-1001';
UPDATE customer SET email = 'ravi.singh@example.test' WHERE public_id = 'CUS-1002';

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX ix_customer_full_name_trgm
  ON customer USING GIN (upper(full_name) gin_trgm_ops);

DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'crm_app') THEN
    CREATE ROLE crm_app NOLOGIN;
  END IF;
END
$$;

GRANT USAGE ON SCHEMA public TO crm_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO crm_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO crm_app;

ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO crm_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public
  GRANT USAGE, SELECT ON SEQUENCES TO crm_app;

REVOKE ALL ON flyway_schema_history FROM crm_app;

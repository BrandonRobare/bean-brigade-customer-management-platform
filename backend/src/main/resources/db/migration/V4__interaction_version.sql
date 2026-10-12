ALTER TABLE customer_interaction
  ADD COLUMN version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0);

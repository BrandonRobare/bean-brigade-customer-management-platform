CREATE TABLE processed_event (
  event_id      UUID PRIMARY KEY,
  processed_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE patient_event_log (
  patient_id UUID NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  received_at TIMESTAMP(6) NOT NULL DEFAULT now(),
  CONSTRAINT patient_event_log_pkey PRIMARY KEY (patient_id, event_type)
)

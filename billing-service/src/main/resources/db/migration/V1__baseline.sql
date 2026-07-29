CREATE TABLE billing_accounts (
  id UUID NOT NULL,
  account_id VARCHAR(10) NOT NULL,
  patient_id VARCHAR(36) NOT NULL,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  balance NUMERIC(19, 2) NOT NULL,
  credit_limit NUMERIC(19, 2) NOT NULL,
  currency VARCHAR(10) NOT NULL,
  status VARCHAR(10) NOT NULL,
  billing_cycle_start TIMESTAMP(6) NOT NULL,
  CONSTRAINT billing_accounts_currency_check CHECK (currency = 'NGN'),
  CONSTRAINT billing_accounts_status_check CHECK (
    status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')
  )
);
ALTER TABLE billing_accounts
ADD CONSTRAINT billing_accounts_pkey PRIMARY KEY (id);
ALTER TABLE billing_accounts
ADD CONSTRAINT uk_billing_accounts_account_id UNIQUE (account_id);
ALTER TABLE billing_accounts
ADD CONSTRAINT uk_billing_accounts_patient_id UNIQUE (patient_id);
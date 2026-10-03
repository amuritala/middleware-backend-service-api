-- Banking Database Init Script
-- This script runs on first startup of postgres-banking container

-- Create extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Create schemas if needed
CREATE SCHEMA IF NOT EXISTS banking;

-- Set default privileges
ALTER DEFAULT PRIVILEGES IN SCHEMA banking GRANT ALL ON TABLES TO banking_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA banking GRANT ALL ON SEQUENCES TO banking_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA banking GRANT ALL ON FUNCTIONS TO banking_user;

-- Create audit table (optional)
CREATE TABLE IF NOT EXISTS banking.audit_log (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    table_name VARCHAR(100),
    operation VARCHAR(20),
    record_id UUID,
    user_id UUID,
    old_data JSONB,
    new_data JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Create function for updating updated_at
CREATE OR REPLACE FUNCTION banking.update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Create function for audit logging (optional)
CREATE OR REPLACE FUNCTION banking.log_audit()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO banking.audit_log (
        table_name,
        operation,
        record_id,
        old_data,
        new_data
    ) VALUES (
        TG_TABLE_NAME,
        TG_OP,
        COALESCE(NEW.id, OLD.id),
        CASE WHEN TG_OP = 'DELETE' THEN row_to_json(OLD) ELSE NULL END,
        CASE WHEN TG_OP IN ('INSERT', 'UPDATE') THEN row_to_json(NEW) ELSE NULL END
    );
    RETURN NULL;
END;
$$ language 'plpgsql';

-- Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON banking.audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_operation ON banking.audit_log(operation);

-- Grant privileges
GRANT ALL PRIVILEGES ON SCHEMA banking TO banking_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA banking TO banking_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA banking TO banking_user;
GRANT ALL PRIVILEGES ON ALL FUNCTIONS IN SCHEMA banking TO banking_user;

-- Set search path
ALTER DATABASE banking_db SET search_path TO banking, public;

-- Display success message
DO $$
BEGIN
    RAISE NOTICE 'Banking database initialization completed successfully!';
END $$;
CREATE TABLE idempotency_record (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_scope VARCHAR(160) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    response_status INTEGER NOT NULL,
    response_body JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT idempotency_record_scope_key_uk UNIQUE (operation_scope, idempotency_key)
);

CREATE INDEX idempotency_record_scope_created_at_idx
    ON idempotency_record (operation_scope, created_at DESC);

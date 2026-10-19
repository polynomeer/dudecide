ALTER TABLE chat_import ADD COLUMN claimed_at TIMESTAMPTZ;
ALTER TABLE chat_import ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0);

CREATE INDEX chat_import_status_claimed_idx ON chat_import (status, claimed_at);
CREATE INDEX chat_import_uploader_created_idx ON chat_import (uploaded_by_user_id, created_at);

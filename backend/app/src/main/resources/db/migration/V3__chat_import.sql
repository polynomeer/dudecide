CREATE TYPE message_origin AS ENUM (
    'native',
    'imported'
);

CREATE TYPE chat_import_source AS ENUM (
    'kakao_export',
    'paste',
    'screenshot',
    'discord'
);

CREATE TYPE chat_import_status AS ENUM (
    'submitted',
    'analyzing',
    'ready',
    'applied',
    'failed',
    'deleted'
);

CREATE TABLE chat_import (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    thread_id UUID NOT NULL REFERENCES topic_thread(id) ON DELETE CASCADE,
    uploaded_by_user_id UUID NOT NULL REFERENCES app_user(id),
    source_type chat_import_source NOT NULL,
    source_format VARCHAR(64) NOT NULL,
    source_room_name VARCHAR(200),
    range_start TIMESTAMPTZ NOT NULL,
    range_end TIMESTAMPTZ NOT NULL,
    message_count INTEGER NOT NULL CHECK (message_count >= 0),
    duplicate_count INTEGER NOT NULL DEFAULT 0 CHECK (duplicate_count >= 0),
    status chat_import_status NOT NULL DEFAULT 'submitted',
    progress JSONB NOT NULL DEFAULT '{}'::JSONB,
    result JSONB,
    stats JSONB NOT NULL DEFAULT '{}'::JSONB,
    consent_version VARCHAR(64) NOT NULL,
    prompt_version VARCHAR(64),
    model_versions JSONB NOT NULL DEFAULT '{}'::JSONB,
    failure_reason VARCHAR(200),
    purge_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    CONSTRAINT chat_import_range_ck CHECK (range_start <= range_end)
);

CREATE TABLE import_speaker (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    thread_id UUID NOT NULL REFERENCES topic_thread(id) ON DELETE CASCADE,
    display_name VARCHAR(120) NOT NULL,
    pseudonym VARCHAR(32) NOT NULL,
    membership_id UUID REFERENCES membership(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT import_speaker_thread_name_uk UNIQUE (thread_id, display_name),
    CONSTRAINT import_speaker_thread_pseudonym_uk UNIQUE (thread_id, pseudonym)
);

ALTER TABLE topic_thread ADD COLUMN read_only BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE message ALTER COLUMN sender_user_id DROP NOT NULL;
ALTER TABLE message ADD COLUMN origin message_origin NOT NULL DEFAULT 'native';
ALTER TABLE message ADD COLUMN import_id UUID;
ALTER TABLE message ADD COLUMN import_speaker_id UUID;
ALTER TABLE message ADD COLUMN content_hash CHAR(64);
ALTER TABLE message ADD CONSTRAINT message_import_fk
    FOREIGN KEY (import_id) REFERENCES chat_import(id) ON DELETE CASCADE;
ALTER TABLE message ADD CONSTRAINT message_import_speaker_fk
    FOREIGN KEY (import_speaker_id) REFERENCES import_speaker(id) ON DELETE CASCADE;
ALTER TABLE message ADD CONSTRAINT message_sender_origin_ck CHECK (
    (origin = 'native' AND sender_user_id IS NOT NULL AND import_speaker_id IS NULL)
    OR (
        origin = 'imported'
        AND sender_user_id IS NULL
        AND import_speaker_id IS NOT NULL
        AND import_id IS NOT NULL
        AND content_hash IS NOT NULL
    )
);

CREATE UNIQUE INDEX message_thread_content_hash_uk ON message (thread_id, content_hash)
    WHERE content_hash IS NOT NULL;
CREATE INDEX message_import_id_idx ON message (import_id);
CREATE INDEX chat_import_meeting_idx ON chat_import (meeting_id, created_at DESC);
CREATE INDEX chat_import_thread_idx ON chat_import (thread_id, created_at DESC);
CREATE INDEX chat_import_purge_idx ON chat_import (purge_at) WHERE purge_at IS NOT NULL;
CREATE INDEX import_speaker_membership_idx ON import_speaker (membership_id);

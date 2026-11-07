ALTER TABLE topic_thread ADD COLUMN suggestions_analyzed_at TIMESTAMPTZ;

CREATE TABLE decision_suggestion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    thread_id UUID NOT NULL REFERENCES topic_thread(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    analysis_status VARCHAR(32) NOT NULL,
    decision_type decision_type NOT NULL,
    title VARCHAR(200) NOT NULL,
    proposed_result JSONB NOT NULL DEFAULT '{}'::JSONB,
    evidence JSONB NOT NULL DEFAULT '[]'::JSONB,
    decision_id UUID REFERENCES decision(id) ON DELETE SET NULL,
    resolved_by_user_id UUID REFERENCES app_user(id) ON DELETE SET NULL,
    resolved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT decision_suggestion_status_ck CHECK (status IN ('pending', 'accepted', 'dismissed'))
);

-- One row per live analysis, kept even when it finds nothing; backs the per-meeting daily cap.
CREATE TABLE decision_suggestion_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    thread_id UUID NOT NULL REFERENCES topic_thread(id) ON DELETE CASCADE,
    message_count INTEGER NOT NULL,
    suggestion_count INTEGER NOT NULL DEFAULT 0,
    failure_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX decision_suggestion_run_meeting_created_idx ON decision_suggestion_run (meeting_id, created_at);
CREATE INDEX decision_suggestion_thread_status_idx ON decision_suggestion (thread_id, status);
CREATE INDEX decision_suggestion_meeting_created_idx ON decision_suggestion (meeting_id, created_at);
CREATE INDEX topic_thread_suggestion_scan_idx ON topic_thread (last_activity_at, suggestions_analyzed_at);

CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TYPE meeting_status AS ENUM (
    'draft',
    'active',
    'finalizing',
    'completed',
    'archived'
);

CREATE TYPE meeting_role AS ENUM (
    'host',
    'co_host',
    'participant',
    'guest'
);

CREATE TYPE attendance_status AS ENUM (
    'pending',
    'accepted',
    'declined',
    'tentative'
);

CREATE TYPE thread_category AS ENUM (
    'date',
    'location',
    'lodging',
    'transport',
    'budget',
    'tasking',
    'free_chat'
);

CREATE TYPE thread_status AS ENUM (
    'active',
    'blocked',
    'waiting_on_dependency',
    'closed'
);

CREATE TYPE summary_type AS ENUM (
    'thread_snapshot',
    'decision_digest',
    'search_answer_snippet'
);

CREATE TYPE decision_type AS ENUM (
    'date',
    'location',
    'lodging',
    'transport',
    'budget',
    'task',
    'other'
);

CREATE TYPE decision_status AS ENUM (
    'proposed',
    'in_discussion',
    'voting',
    'confirmed',
    'on_hold',
    'needs_reconsideration'
);

CREATE TYPE evidence_type AS ENUM (
    'proposal',
    'support',
    'opposition',
    'confirmation',
    'vote_result',
    'condition'
);

CREATE TYPE decision_history_action AS ENUM (
    'drafted',
    'updated',
    'confirmed',
    'reopened',
    'superseded',
    'cancelled'
);

CREATE TYPE vote_type AS ENUM (
    'date_matrix',
    'package_preference',
    'binary_confirm'
);

CREATE TYPE vote_status AS ENUM (
    'draft',
    'open',
    'closed',
    'superseded'
);

CREATE TYPE vote_response_value AS ENUM (
    'available',
    'maybe',
    'unavailable',
    'good',
    'okay',
    'dislike',
    'yes',
    'no'
);

CREATE TYPE task_status AS ENUM (
    'todo',
    'in_progress',
    'done',
    'cancelled'
);

CREATE TYPE notification_type AS ENUM (
    'invite_created',
    'invite_joined',
    'vote_reminder',
    'vote_closed',
    'decision_suggested',
    'decision_confirmed',
    'discussion_stalled',
    'report_ready'
);

CREATE TYPE outbox_status AS ENUM (
    'pending',
    'published',
    'failed'
);

CREATE TABLE app_user (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    display_name VARCHAR(120) NOT NULL,
    avatar_url TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE meeting (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_user_id UUID NOT NULL REFERENCES app_user(id),
    title VARCHAR(200) NOT NULL,
    meeting_type VARCHAR(32) NOT NULL,
    status meeting_status NOT NULL DEFAULT 'draft',
    origin_region VARCHAR(120),
    expected_headcount INTEGER CHECK (expected_headcount IS NULL OR expected_headcount > 0),
    budget_min NUMERIC(12, 2),
    budget_max NUMERIC(12, 2),
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT meeting_budget_range_chk CHECK (
        budget_min IS NULL
        OR budget_max IS NULL
        OR budget_min <= budget_max
    ),
    CONSTRAINT meeting_time_range_chk CHECK (
        starts_at IS NULL
        OR ends_at IS NULL
        OR starts_at <= ends_at
    )
);

CREATE TABLE membership (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    role meeting_role NOT NULL,
    attendance_status attendance_status NOT NULL DEFAULT 'pending',
    joined_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT membership_meeting_user_uk UNIQUE (meeting_id, user_id)
);

CREATE TABLE invite_link (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    token VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'active',
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT invite_link_token_uk UNIQUE (token)
);

CREATE TABLE topic_thread (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    category thread_category NOT NULL,
    title VARCHAR(200) NOT NULL,
    status thread_status NOT NULL DEFAULT 'active',
    sort_order INTEGER NOT NULL DEFAULT 0,
    last_activity_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id UUID NOT NULL REFERENCES topic_thread(id) ON DELETE CASCADE,
    sender_user_id UUID NOT NULL REFERENCES app_user(id),
    body TEXT NOT NULL,
    attachments JSONB NOT NULL DEFAULT '[]'::JSONB,
    ai_labels JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    edited_at TIMESTAMPTZ
);

CREATE TABLE thread_summary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    thread_id UUID NOT NULL REFERENCES topic_thread(id) ON DELETE CASCADE,
    summary_type summary_type NOT NULL,
    content TEXT NOT NULL,
    source_message_ids JSONB NOT NULL DEFAULT '[]'::JSONB,
    confidence NUMERIC(5, 4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE decision (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    thread_id UUID NOT NULL REFERENCES topic_thread(id) ON DELETE CASCADE,
    decided_by_user_id UUID REFERENCES app_user(id),
    decision_type decision_type NOT NULL,
    title VARCHAR(200) NOT NULL,
    status decision_status NOT NULL DEFAULT 'proposed',
    proposed_result JSONB NOT NULL DEFAULT '{}'::JSONB,
    confirmed_result JSONB NOT NULL DEFAULT '{}'::JSONB,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE decision_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    decision_id UUID NOT NULL REFERENCES decision(id) ON DELETE CASCADE,
    message_id UUID NOT NULL REFERENCES message(id) ON DELETE CASCADE,
    thread_id UUID NOT NULL REFERENCES topic_thread(id) ON DELETE CASCADE,
    evidence_type evidence_type NOT NULL,
    quote_snapshot TEXT NOT NULL,
    confidence NUMERIC(5, 4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT decision_evidence_dedup_uk UNIQUE (decision_id, message_id, evidence_type)
);

CREATE TABLE decision_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    decision_id UUID NOT NULL REFERENCES decision(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES app_user(id),
    action decision_history_action NOT NULL,
    before_state JSONB NOT NULL DEFAULT '{}'::JSONB,
    after_state JSONB NOT NULL DEFAULT '{}'::JSONB,
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE vote (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    decision_id UUID REFERENCES decision(id) ON DELETE SET NULL,
    thread_id UUID NOT NULL REFERENCES topic_thread(id) ON DELETE CASCADE,
    vote_type vote_type NOT NULL,
    status vote_status NOT NULL DEFAULT 'draft',
    scoring_rule JSONB NOT NULL DEFAULT '{}'::JSONB,
    opens_at TIMESTAMPTZ,
    deadline_at TIMESTAMPTZ,
    closed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT vote_time_window_chk CHECK (
        opens_at IS NULL
        OR deadline_at IS NULL
        OR opens_at <= deadline_at
    ),
    CONSTRAINT vote_close_time_chk CHECK (
        closed_at IS NULL
        OR deadline_at IS NULL
        OR deadline_at <= closed_at
    )
);

CREATE TABLE vote_option (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vote_id UUID NOT NULL REFERENCES vote(id) ON DELETE CASCADE,
    option_type VARCHAR(32) NOT NULL,
    label VARCHAR(200) NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::JSONB,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE vote_response (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vote_id UUID NOT NULL REFERENCES vote(id) ON DELETE CASCADE,
    option_id UUID NOT NULL REFERENCES vote_option(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    response_value vote_response_value NOT NULL,
    response_payload JSONB NOT NULL DEFAULT '{}'::JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT vote_response_vote_option_user_uk UNIQUE (vote_id, option_id, user_id)
);

CREATE TABLE task_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    owner_user_id UUID REFERENCES app_user(id),
    source_message_id UUID REFERENCES message(id) ON DELETE SET NULL,
    title VARCHAR(200) NOT NULL,
    status task_status NOT NULL DEFAULT 'todo',
    due_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE meeting_report (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    version INTEGER NOT NULL CHECK (version > 0),
    snapshot JSONB NOT NULL DEFAULT '{}'::JSONB,
    generated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT meeting_report_meeting_version_uk UNIQUE (meeting_id, version)
);

CREATE TABLE notification (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID NOT NULL REFERENCES meeting(id) ON DELETE CASCADE,
    recipient_user_id UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    type notification_type NOT NULL,
    payload JSONB NOT NULL DEFAULT '{}'::JSONB,
    sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE outbox_event (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    status outbox_status NOT NULL DEFAULT 'pending',
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ
);

CREATE INDEX membership_user_id_idx ON membership (user_id);
CREATE INDEX invite_link_meeting_id_idx ON invite_link (meeting_id);
CREATE INDEX topic_thread_meeting_id_idx ON topic_thread (meeting_id, sort_order);
CREATE INDEX topic_thread_last_activity_idx ON topic_thread (meeting_id, last_activity_at DESC);
CREATE INDEX message_thread_created_at_idx ON message (thread_id, created_at DESC);
CREATE INDEX message_sender_user_id_idx ON message (sender_user_id);
CREATE INDEX thread_summary_thread_id_idx ON thread_summary (thread_id, created_at DESC);
CREATE INDEX decision_meeting_status_idx ON decision (meeting_id, status);
CREATE INDEX decision_thread_id_idx ON decision (thread_id);
CREATE INDEX decision_history_decision_id_idx ON decision_history (decision_id, created_at DESC);
CREATE INDEX decision_evidence_decision_id_idx ON decision_evidence (decision_id);
CREATE INDEX decision_evidence_message_id_idx ON decision_evidence (message_id);
CREATE INDEX vote_meeting_status_idx ON vote (meeting_id, status);
CREATE INDEX vote_decision_id_idx ON vote (decision_id);
CREATE INDEX vote_option_vote_id_idx ON vote_option (vote_id, sort_order);
CREATE INDEX vote_response_vote_id_idx ON vote_response (vote_id);
CREATE INDEX vote_response_user_id_idx ON vote_response (user_id);
CREATE INDEX task_item_meeting_id_idx ON task_item (meeting_id, status);
CREATE INDEX task_item_owner_user_id_idx ON task_item (owner_user_id);
CREATE INDEX meeting_report_meeting_id_idx ON meeting_report (meeting_id, generated_at DESC);
CREATE INDEX notification_recipient_created_at_idx ON notification (recipient_user_id, created_at DESC);
CREATE INDEX outbox_event_status_occurred_at_idx ON outbox_event (status, occurred_at);

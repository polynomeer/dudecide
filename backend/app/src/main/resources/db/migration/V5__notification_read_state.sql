ALTER TABLE notification ADD COLUMN read_at TIMESTAMPTZ;

CREATE INDEX notification_recipient_meeting_read_idx ON notification (recipient_user_id, meeting_id, read_at);

INSERT INTO app_user (id, display_name, status)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'Local Host', 'active'),
    ('00000000-0000-0000-0000-000000000002', 'Demo Participant', 'active')
ON CONFLICT (id) DO NOTHING;

INSERT INTO meeting (
    id,
    owner_user_id,
    title,
    meeting_type,
    status,
    origin_region,
    expected_headcount,
    budget_min,
    budget_max
)
VALUES (
    '10000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    'Seeded Autumn Trip',
    'trip',
    'active',
    'Seoul',
    4,
    120000,
    200000
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO membership (id, meeting_id, user_id, role, attendance_status)
VALUES
    ('11000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'host', 'accepted'),
    ('11000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'participant', 'accepted')
ON CONFLICT (meeting_id, user_id) DO NOTHING;

INSERT INTO topic_thread (id, meeting_id, category, title, status, sort_order)
VALUES
    ('20000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'date', 'Date', 'active', 0),
    ('20000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000001', 'location', 'Location', 'active', 1),
    ('20000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000001', 'lodging', 'Lodging', 'active', 2),
    ('20000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000001', 'transport', 'Transport', 'active', 3),
    ('20000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000001', 'free_chat', 'Free Chat', 'active', 4)
ON CONFLICT (id) DO NOTHING;

INSERT INTO decision (
    id,
    meeting_id,
    thread_id,
    decision_type,
    title,
    status,
    proposed_result,
    confirmed_result
)
VALUES (
    '30000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'date',
    'Seeded Autumn Trip Date',
    'voting',
    '{}'::jsonb,
    '{}'::jsonb
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO vote (
    id,
    meeting_id,
    decision_id,
    thread_id,
    vote_type,
    status,
    scoring_rule
)
VALUES (
    '40000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000001',
    '30000000-0000-0000-0000-000000000001',
    '20000000-0000-0000-0000-000000000001',
    'date_matrix',
    'open',
    '{"version":"mvp-v1","model":"date_matrix","weights":{"available":1.0,"maybe":0.5,"unavailable":-2.0}}'::jsonb
)
ON CONFLICT (id) DO NOTHING;

INSERT INTO vote_option (id, vote_id, option_type, label, payload, sort_order)
VALUES
    ('41000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001', 'date', '2026-10-10', '{"date":"2026-10-10"}'::jsonb, 0),
    ('41000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000001', 'date', '2026-10-17', '{"date":"2026-10-17"}'::jsonb, 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO vote_response (id, vote_id, option_id, user_id, response_value, response_payload)
VALUES
    ('42000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001', '41000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'available', '{}'::jsonb),
    ('42000000-0000-0000-0000-000000000002', '40000000-0000-0000-0000-000000000001', '41000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'maybe', '{}'::jsonb),
    ('42000000-0000-0000-0000-000000000003', '40000000-0000-0000-0000-000000000001', '41000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000002', 'available', '{}'::jsonb),
    ('42000000-0000-0000-0000-000000000004', '40000000-0000-0000-0000-000000000001', '41000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000002', 'unavailable', '{}'::jsonb)
ON CONFLICT (vote_id, option_id, user_id) DO NOTHING;

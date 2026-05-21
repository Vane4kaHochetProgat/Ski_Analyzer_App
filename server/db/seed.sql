BEGIN;

INSERT INTO sports (code, name) VALUES
    ('skiing',       'Skiing'),
    ('snowboarding', 'Snowboarding'),
    ('both',         'Both')
ON CONFLICT (code) DO NOTHING;

INSERT INTO severities (code, label) VALUES
    ('low',    'low'),
    ('medium', 'medium'),
    ('high',   'high')
ON CONFLICT (code) DO NOTHING;

INSERT INTO skill_levels (name) VALUES
    ('Beginner'),
    ('Intermediate'),
    ('Advanced'),
    ('Expert')
ON CONFLICT (name) DO NOTHING;

INSERT INTO languages (code, name) VALUES
    ('en-US', 'English'),
    ('ru-RU', 'Russian')
ON CONFLICT (code) DO NOTHING;

INSERT INTO mistake_types (code, title, description, default_severity_id, sport_id, icon_code, icon_tint_hex) VALUES
    ('leaning_back',        'Leaning Back',
        'Weight distribution too far back, reducing control',
        (SELECT severity_id FROM severities WHERE code='high'),
        (SELECT sport_id    FROM sports     WHERE code='skiing'),
        'warning',     '#F59E0B'),
    ('arms_too_wide',       'Arms Too Wide',
        'Poor balance and reduced turning efficiency',
        (SELECT severity_id FROM severities WHERE code='medium'),
        (SELECT sport_id    FROM sports     WHERE code='snowboarding'),
        'swap_horiz',  '#6B7280'),
    ('looking_down',        'Looking Down',
        'Eyes focused on skis instead of ahead',
        (SELECT severity_id FROM severities WHERE code='high'),
        (SELECT sport_id    FROM sports     WHERE code='both'),
        'eye',         '#334155'),
    ('stiff_knees',         'Stiff Knees',
        'Not absorbing terrain properly',
        (SELECT severity_id FROM severities WHERE code='medium'),
        (SELECT sport_id    FROM sports     WHERE code='skiing'),
        'directions_run', '#F59E0B'),
    ('hip_rotation_issues', 'Hip Rotation Issues',
        'Hips not aligning properly during turns',
        (SELECT severity_id FROM severities WHERE code='low'),
        (SELECT sport_id    FROM sports     WHERE code='both'),
        'rotate_right',   '#6B7280')
ON CONFLICT (code) DO NOTHING;

COMMIT;

BEGIN;

CREATE TABLE sports (
    sport_id   SERIAL PRIMARY KEY,
    code       VARCHAR(32)  UNIQUE NOT NULL,
    name       VARCHAR(64)  NOT NULL
);

CREATE TABLE severities (
    severity_id SERIAL PRIMARY KEY,
    code        VARCHAR(16) UNIQUE NOT NULL,
    label       VARCHAR(32) NOT NULL
);

CREATE TABLE skill_levels (
    skill_level_id SERIAL PRIMARY KEY,
    name           VARCHAR(64) UNIQUE NOT NULL
);

CREATE TABLE languages (
    language_id SERIAL PRIMARY KEY,
    code        VARCHAR(16) UNIQUE NOT NULL,
    name        VARCHAR(64) NOT NULL
);

CREATE TABLE users (
    user_id       SERIAL PRIMARY KEY,
    username      VARCHAR(64)  UNIQUE NOT NULL,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE photos (
    photo_id    SERIAL PRIMARY KEY,
    user_id     INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    file_path   TEXT NOT NULL,
    mime_type   VARCHAR(64),
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    width       INT,
    height      INT
);

CREATE TABLE user_profiles (
    user_id            INT PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    display_name       VARCHAR(128),
    skill_level_id     INT REFERENCES skill_levels(skill_level_id),
    preferred_sport_id INT REFERENCES sports(sport_id),
    avatar_photo_id    INT REFERENCES photos(photo_id) ON DELETE SET NULL
);

CREATE TABLE user_preferences (
    user_id               INT PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    theme                 VARCHAR(32),
    language_id           INT REFERENCES languages(language_id),
    notifications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    timezone              VARCHAR(64)
);

CREATE TABLE videos (
    video_id        SERIAL PRIMARY KEY,
    user_id         INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    sport_id        INT NOT NULL REFERENCES sports(sport_id),
    file_path       TEXT NOT NULL,
    annotated_path  TEXT,
    uploaded_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_videos_user ON videos(user_id);

CREATE TYPE analysis_status AS ENUM ('pending', 'completed', 'failed');

CREATE TABLE analyses (
    analysis_id   SERIAL PRIMARY KEY,
    video_id      INT NOT NULL UNIQUE REFERENCES videos(video_id) ON DELETE CASCADE,
    overall_score NUMERIC(5,2),
    charts_path   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    status        analysis_status NOT NULL DEFAULT 'pending'
);

CREATE TABLE angle_analyses (
    angle_analysis_id SERIAL PRIMARY KEY,
    analysis_id       INT NOT NULL REFERENCES analyses(analysis_id) ON DELETE CASCADE,
    angle             VARCHAR(64) NOT NULL,
    percent_bad       NUMERIC(5,2) NOT NULL,
    mean_diff_deg     NUMERIC(6,2) NOT NULL,
    max_diff_deg      NUMERIC(6,2) NOT NULL,
    is_critical       BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX idx_angle_analyses_analysis ON angle_analyses(analysis_id);

CREATE TABLE recommendations (
    recommendation_id SERIAL PRIMARY KEY,
    analysis_id       INT NOT NULL REFERENCES analyses(analysis_id) ON DELETE CASCADE,
    text              TEXT NOT NULL,
    position          INT  NOT NULL
);
CREATE INDEX idx_recommendations_analysis ON recommendations(analysis_id);

CREATE TABLE mistake_types (
    mistake_type_id     SERIAL PRIMARY KEY,
    code                VARCHAR(64) UNIQUE NOT NULL,
    title               VARCHAR(128) NOT NULL,
    description         TEXT NOT NULL,
    default_severity_id INT NOT NULL REFERENCES severities(severity_id),
    sport_id            INT NOT NULL REFERENCES sports(sport_id),
    icon_code           VARCHAR(64),
    icon_tint_hex       CHAR(7)
);

CREATE TABLE user_mistakes (
    user_mistake_id SERIAL PRIMARY KEY,
    user_id         INT NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    analysis_id     INT NOT NULL REFERENCES analyses(analysis_id) ON DELETE CASCADE,
    mistake_type_id INT NOT NULL REFERENCES mistake_types(mistake_type_id),
    severity_id     INT NOT NULL REFERENCES severities(severity_id),
    detected_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    notes           TEXT,
    UNIQUE (analysis_id, mistake_type_id)
);
CREATE INDEX idx_user_mistakes_user ON user_mistakes(user_id);
CREATE INDEX idx_user_mistakes_type ON user_mistakes(mistake_type_id);

CREATE VIEW mistake_user_stats AS
SELECT
    mt.mistake_type_id,
    mt.code,
    mt.title,
    COUNT(DISTINCT um.user_id)::numeric
        / NULLIF((SELECT COUNT(*) FROM users WHERE is_active), 0) AS user_share
FROM mistake_types mt
LEFT JOIN user_mistakes um ON um.mistake_type_id = mt.mistake_type_id
GROUP BY mt.mistake_type_id, mt.code, mt.title;

COMMIT;

-- One uploaded screenshot and its analysis result.
CREATE TABLE analysis (
    id                  UUID PRIMARY KEY,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    status              VARCHAR(16)  NOT NULL,
    image_sha256        VARCHAR(64)  NOT NULL CHECK (image_sha256 ~ '^[0-9a-f]{64}$'),
    image_media_type    VARCHAR(32)  NOT NULL,
    image_width         INTEGER      NOT NULL CHECK (image_width > 0),
    image_height        INTEGER      NOT NULL CHECK (image_height > 0),
    image_data          BYTEA        NOT NULL,
    provider            VARCHAR(32),
    model               VARCHAR(64),
    summary             TEXT,
    score_accessibility SMALLINT CHECK (score_accessibility BETWEEN 0 AND 100),
    score_hierarchy     SMALLINT CHECK (score_hierarchy BETWEEN 0 AND 100),
    score_consistency   SMALLINT CHECK (score_consistency BETWEEN 0 AND 100),
    error_message       TEXT,
    CONSTRAINT analysis_status_chk CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED'))
);

-- Lookup by image hash lets us reuse a previous result instead of paying for a new analysis.
CREATE INDEX analysis_image_sha256_idx ON analysis (image_sha256);
CREATE INDEX analysis_created_at_idx ON analysis (created_at DESC);

-- One usability / accessibility problem found in an analysis.
-- The bounding box is normalized to the image size (0..1), so it is independent of display size.
CREATE TABLE issue (
    id           UUID PRIMARY KEY,
    analysis_id  UUID         NOT NULL REFERENCES analysis (id) ON DELETE CASCADE,
    position     INTEGER      NOT NULL,
    title        VARCHAR(200) NOT NULL,
    description  TEXT         NOT NULL,
    severity     VARCHAR(16)  NOT NULL,
    category     VARCHAR(32)  NOT NULL,
    criterion_id VARCHAR(64),
    suggestion   TEXT         NOT NULL,
    bbox_x       REAL,
    bbox_y       REAL,
    bbox_w       REAL,
    bbox_h       REAL,
    CONSTRAINT issue_severity_chk CHECK (severity IN ('CRITICAL', 'MAJOR', 'MINOR')),
    CONSTRAINT issue_category_chk CHECK (category IN ('ACCESSIBILITY', 'VISUAL_HIERARCHY', 'CONSISTENCY', 'USABILITY')),
    -- Either no box at all, or a complete box that stays inside the image.
    CONSTRAINT issue_bbox_chk CHECK (
        (bbox_x IS NULL AND bbox_y IS NULL AND bbox_w IS NULL AND bbox_h IS NULL)
        OR (bbox_x >= 0 AND bbox_y >= 0 AND bbox_w > 0 AND bbox_h > 0
            AND bbox_x + bbox_w <= 1.0001 AND bbox_y + bbox_h <= 1.0001)
    ),
    CONSTRAINT issue_position_uq UNIQUE (analysis_id, position)
);

-- Token usage and cost of every model call, for the cost section of the README and the daily budget guard.
CREATE TABLE api_usage (
    id                BIGSERIAL PRIMARY KEY,
    analysis_id       UUID REFERENCES analysis (id) ON DELETE SET NULL,
    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    provider          VARCHAR(32)   NOT NULL,
    model             VARCHAR(64)   NOT NULL,
    input_tokens      INTEGER       NOT NULL DEFAULT 0,
    output_tokens     INTEGER       NOT NULL DEFAULT 0,
    cache_read_tokens INTEGER       NOT NULL DEFAULT 0,
    cost_usd          NUMERIC(12,6) NOT NULL DEFAULT 0
);

CREATE INDEX api_usage_created_at_idx ON api_usage (created_at);

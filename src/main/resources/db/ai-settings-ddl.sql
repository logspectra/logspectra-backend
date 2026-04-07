CREATE TABLE IF NOT EXISTS ai_settings (
    id UUID PRIMARY KEY,
    provider VARCHAR(20) NOT NULL,
    api_key TEXT,
    model_name VARCHAR(100) NOT NULL,
    base_url VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_ai_settings_provider ON ai_settings(provider);
CREATE UNIQUE INDEX IF NOT EXISTS ux_ai_settings_single_active ON ai_settings ((is_active)) WHERE is_active = TRUE;

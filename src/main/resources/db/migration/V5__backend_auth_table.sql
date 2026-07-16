CREATE TABLE IF NOT EXISTS proxy_auth (
    code          VARCHAR(64)  PRIMARY KEY,
    auth_type     VARCHAR(31)  NOT NULL,

    -- OAUTH2 columns
    url           TEXT,
    client_id     TEXT,
    client_secret TEXT,
    scope         TEXT,
    grant_type    TEXT,
    oauth2_type   VARCHAR(16),

    -- BASIC columns
    username      TEXT,
    password      TEXT
);

ALTER TABLE proxy_backends
    ADD COLUMN IF NOT EXISTS auth_code VARCHAR(64) REFERENCES proxy_auth(code);

CREATE INDEX IF NOT EXISTS idx_proxy_backends_auth_code ON proxy_backends(auth_code);

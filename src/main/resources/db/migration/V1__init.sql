CREATE TABLE proxy_globals (
    id                 SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    connect_timeout_ms INTEGER NOT NULL DEFAULT 5000,
    read_timeout_ms    INTEGER NOT NULL DEFAULT 30000,
    forward_headers    TEXT[]  NOT NULL DEFAULT ARRAY[]::TEXT[],
    strip_headers      TEXT[]  NOT NULL DEFAULT ARRAY[]::TEXT[]
);

CREATE TABLE proxy_backends (
    id                 VARCHAR(64) PRIMARY KEY,
    base_url           TEXT NOT NULL,
    connect_timeout_ms INTEGER,
    read_timeout_ms    INTEGER
);

CREATE TABLE proxy_routes (
    id         VARCHAR(128) PRIMARY KEY,
    source     TEXT NOT NULL,
    target     TEXT NOT NULL,
    backend_id VARCHAR(64) NOT NULL REFERENCES proxy_backends(id),
    methods    TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
    produces   TEXT
);

CREATE TABLE proxy_route_transforms (
    id        BIGSERIAL PRIMARY KEY,
    route_id  VARCHAR(128) NOT NULL REFERENCES proxy_routes(id) ON DELETE CASCADE,
    ordinal   INTEGER NOT NULL,
    from_ref  TEXT NOT NULL,
    to_ref    TEXT,
    UNIQUE (route_id, ordinal)
);

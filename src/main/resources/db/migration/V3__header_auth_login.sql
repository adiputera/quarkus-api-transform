INSERT INTO proxy_routes (id, source, target, backend_id, methods, produces) VALUES
    ('header-auth-login', '/header-login', '/auth/token', 'auth-api', ARRAY['POST'], 'application/json');

INSERT INTO proxy_route_transforms (route_id, ordinal, from_ref, to_ref) VALUES
    ('header-auth-login', 0, 'body:/apiKey',   'header:X-API-Key'),
    ('header-auth-login', 1, 'body:/username', 'body:/user');

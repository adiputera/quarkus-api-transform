-- Update existing sample backends to loop back to our local sample/test controllers when running dev/local mode
UPDATE proxy_backends SET base_url = 'http://localhost:8080' WHERE id IN ('product-api', 'order-api', 'auth-api');

-- Seed sample OAuth2 authentication configuration pointing to our local DummyOauth2Resource (/sample/oauth2/token)
INSERT INTO proxy_auth (code, auth_type, url, client_id, client_secret, grant_type, oauth2_type)
VALUES ('sample-oauth2', 'OAUTH2', 'http://localhost:8080/sample/oauth2/token', 'demo-client', 'demo-secret', 'client_credentials', 'HEADER')
ON CONFLICT (code) DO NOTHING;

-- Seed a secure backend using the sample OAuth2 authentication
INSERT INTO proxy_backends (id, base_url, auth_code)
VALUES ('secure-api', 'http://localhost:8080', 'sample-oauth2')
ON CONFLICT (id) DO UPDATE SET base_url = EXCLUDED.base_url, auth_code = EXCLUDED.auth_code;

-- Seed a route that proxies through the secure backend to test token injection and echo validation
INSERT INTO proxy_routes (id, source, target, backend_id, methods)
VALUES ('secure-test-route', '/secure-test', '/sample/secured', 'secure-api', ARRAY['GET', 'POST'])
ON CONFLICT (id) DO UPDATE SET target = EXCLUDED.target, backend_id = EXCLUDED.backend_id;

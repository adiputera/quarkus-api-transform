INSERT INTO proxy_globals (id, connect_timeout_ms, read_timeout_ms, forward_headers, strip_headers)
VALUES (
    1,
    5000,
    30000,
    ARRAY['Authorization', 'Content-Type', 'Accept', 'X-Request-Id', 'X-Correlation-Id'],
    ARRAY['Host', 'Connection', 'Content-Length']
);

INSERT INTO proxy_backends (id, base_url, connect_timeout_ms, read_timeout_ms) VALUES
    ('product-api', 'https://product.internal.example.com', 3000, 15000),
    ('order-api',   'https://order.internal.example.com',   NULL, NULL),
    ('auth-api',    'https://auth.internal.example.com',    NULL, NULL);

INSERT INTO proxy_routes (id, source, target, backend_id, methods, produces) VALUES
    ('get-products-list',      '/get-products',              '/products',            'product-api', ARRAY['GET'],        NULL),
    ('get-product-by-code',    '/get-products/{code}',       '/products/{code}',     'product-api', ARRAY['GET'],        NULL),
    ('get-product-by-query',   '/get-products',              '/products/{code}',     'product-api', ARRAY['GET'],        NULL),
    ('search-products',        '/search-products/{keyword}', '/products/search',     'product-api', ARRAY['GET'],        NULL),
    ('order-by-user',          '/get-orders/{userId}',       '/orders',              'order-api',   ARRAY['GET', 'POST'], NULL),
    ('auth-login',             '/do-login',                  '/auth/token',          'auth-api',    ARRAY['POST'],       NULL),
    ('old-services-catchall',  '/api/old-services/**',       '/api/new-services/**', 'product-api', ARRAY[]::TEXT[],     NULL),
    ('update-product-by-code', '/update-product',            '/products/{code}',     'product-api', ARRAY['POST'],       NULL),
    ('create-product-wrapped', '/create-product',            '/v2/products',         'product-api', ARRAY['POST'],       'application/json'),
    ('legacy-form-login',      '/form-login',                '/auth/token',          'auth-api',    ARRAY['POST'],       'application/json');

INSERT INTO proxy_route_transforms (route_id, ordinal, from_ref, to_ref) VALUES
    ('get-product-by-query',   0, 'query:code',      'path:code'),
    ('search-products',        0, 'path:keyword',    'query:q'),
    ('order-by-user',          0, 'path:userId',     'query:user_id'),
    ('auth-login',             0, 'query:username',  'query:user'),
    ('update-product-by-code', 0, 'body:/code',      'path:code'),
    ('create-product-wrapped', 0, 'body:',           'body:/data'),
    ('legacy-form-login',      0, 'body:/username',  'body:/user'),
    ('legacy-form-login',      1, 'body:/password',  'body:/credentials/secret');

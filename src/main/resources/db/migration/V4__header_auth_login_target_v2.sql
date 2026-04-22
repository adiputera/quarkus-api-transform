UPDATE proxy_routes
SET target = '/auth/v2/token'
WHERE id = 'header-auth-login';

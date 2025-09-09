# Runbook: Authentication Service

Service: auth-service
Domain: authentication

Purpose: Handles login, token issuance/validation, session management, and rate limiting.

Symptoms
- Spikes in 401/403/429
- Users report valid credentials failing ("Invalid credentials")
- Redis connection timeouts or refused connections
- Sudden login latency increase

Quick Checks
1) Deployment: `kubectl rollout history deploy/auth-service`
2) Dependencies:
   - Redis/Session store connectivity and errors
   - Token issuer/OIDC provider status
   - Rate limit counters and config changes
3) Logs: search `redis`, `token`, `rate limit`, `invalid credentials`
4) Config: verify client IDs/secrets, callback URLs, clock skew

Known Patterns
- Redis connection pool exhaustion → 429 and intermittent 401
- Token validation public keys rotated without cache refresh
- Over-aggressive rate limit config after deploy

Playbook Steps
1. Mitigate
   - Temporarily relax rate limits by 25% for login endpoints
   - Increase Redis connection pool max by 20% if headroom exists
2. Validate token path
   - Refresh JWKS cache; verify issuer discovery
   - Check time skew between services (NTP)
3. Redis health
   - Check connection errors, latency, and memory
   - Fail over to replica if primary degraded
4. Rollback if regression correlates with deploy
   - `kubectl rollout undo deploy/auth-service`
5. Communicate
   - Notify support with customer-friendly guidance, workarounds

Rollback
- Revert to previous image; clear config map deltas; warm caches

Keywords
auth-service, redis, token, jwks, oidc, rate limit, 401, 429, timeout, rollback

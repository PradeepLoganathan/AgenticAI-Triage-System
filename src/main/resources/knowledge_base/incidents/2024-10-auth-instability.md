# Incident Report: Authentication Service Instability (P2)

Date: 2024-10-05
Service: auth-service
Version: v1.14.0 (deployed at 09:00Z)
Impact: Intermittent login failures; spikes in 401 and 429; customer complaints

Timeline
- 09:00Z: auth-service v1.14.0 deployed
- 09:05Z: Redis connection timeouts observed
- 09:10Z: ALERT RedisConnectionErrors fired; 429 rate limit rejections increase
- 09:15Z: JWKS cache staleness detected; keys rotated at IdP
- 09:25Z: Increased Redis pool size; relaxed rate limits; forced JWKS refresh
- 09:35Z: Error rates back under thresholds

Root Cause
- Combination of Redis connection pool exhaustion and stale JWKS cache after IdP key rotation.

Remediation
- Increased Redis pool size by 25% (with capacity headroom)
- Forced JWKS cache refresh and set shorter refresh interval
- Tuned rate limit configs for login endpoints

Prevention
- Add health check to detect IdP key rotation
- Pre-warm JWKS on deploy; add Redis connection budget alert

Keywords
auth-service, redis, 401, 429, jwks, token, rate limit, timeout, deployment

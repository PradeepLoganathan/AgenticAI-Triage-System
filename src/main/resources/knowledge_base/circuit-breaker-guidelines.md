# Circuit Breaker Guidelines

Applies to: api-gateway, checkout-service, payment-service, order-service

Principles:
- Fail fast to avoid resource exhaustion
- Use automatic half-open probing before full reopen
- Backoff with jitter to avoid thundering herd

Defaults:
- Error threshold: 20% over 1m
- Minimum requests: 50
- Open state duration: 60s, exponential backoff up to 10m

Runbook:
1. Identify failing upstreams (e.g., checkout-service, payment-service)
2. Open breaker on sustained failures; serve cached fallback if possible
3. Log and alert when breaker state changes
4. Tune thresholds once upstream stabilizes


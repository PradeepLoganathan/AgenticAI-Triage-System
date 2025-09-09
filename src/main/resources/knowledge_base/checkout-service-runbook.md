# Runbook: Checkout Service

Service: checkout-service
Domain: checkout

Purpose: Orchestrates cart review, pricing, promotions, order creation, and handoff to payment-service.

Symptoms
- Elevated 5xx during checkout submit
- Payment step hangs or returns 503
- Inventory validation timeout warnings
- Increased cart abandonment rate

Quick Checks
1) Review recent deployments: `kubectl rollout history deploy/checkout-service`
2) Dependency health:
   - payment-service health: `/health` and error rate
   - inventory-service response latency
   - api-gateway 5xx near checkout path
3) Logs: search for `payment`, `timeout`, `inventory` in checkout-service logs
4) Feature flags: confirm flags for promotions/checkout flows

Known Patterns
- Dependency degradation (payment-service or inventory-service) causes 5xx in checkout
- Misconfigured promotion rule causes CPU spikes and slow response
- Post-deploy cache invalidation missing → stale pricing

Playbook Steps
1. Stabilize user impact
   - Enable graceful degradation: bypass non-critical promo calculations
   - Increase checkout timeouts by 20% temporarily
2. Verify dependencies
   - If payment-service 503 spike: coordinate with payments team, consider queueing orders
   - If inventory timeouts: fall back to cached availability with warnings
3. Rollback if recent deploy correlates
   - `kubectl rollout undo deploy/checkout-service`
   - Validate: response codes, P95, error rate < 2%
4. Clear caches if stale data suspected
   - Invalidate promotions/pricing caches
5. Communicate
   - Notify incident channel; update status page if impact > 5% users

Rollback
- Use previous stable artifact; verify config maps unchanged
- Post-rollback: run synthetic checkout to validate end-to-end

Keywords
checkout-service, payment-service, inventory-service, 503, timeout, promotion, cache, rollback

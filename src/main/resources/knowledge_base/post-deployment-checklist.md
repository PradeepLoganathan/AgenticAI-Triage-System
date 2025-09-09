# Post-Deployment Checklist

Applies to: payment-service, checkout-service, auth-service, order-service

Pre-checks
- Feature flags default state verified
- Config maps and secrets applied correctly
- DB migrations applied and reversible

Smoke Tests
- Health endpoints return 200
- Synthetic user flow: login → add to cart → checkout → payment
- Error rate < 2%, P95 latency within 1.5x baseline

Observability
- Dashboards updated to new version tags
- Alerts: error rate, latency, dependency health
- Log sampling in place; correlation IDs present

Rollback Readiness
- Previous image available
- Rollback steps documented and tested in staging

Keywords
deployment, rollback, feature flag, smoke test, error rate, latency, payment-service, auth-service, checkout-service

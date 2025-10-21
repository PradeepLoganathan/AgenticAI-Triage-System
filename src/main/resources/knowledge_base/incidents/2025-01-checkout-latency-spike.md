# Incident: Checkout Latency Spike

Date: 2025-01-12 18:45 UTC
Services: checkout-service, api-gateway

Summary:
P95 latency increased from 250ms to 1800ms. Spike coincided with checkout-service v2.3.1 deployment.

Findings:
- api-gateway reported upstream timeouts to checkout-service
- Thread pool saturation in checkout-service (queued tasks > 500)
- GC pauses elevated due to memory pressure

Remediation:
- Rolled back checkout-service to v2.3.0
- Increased gateway upstream timeout from 2s to 3s with canary
- Enabled circuit breaker for checkout dependency in order-service

Prevention:
- Add load test gates to CI/CD
- Tune thread pool and GC parameters


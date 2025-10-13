# API Gateway Runbook

Service: api-gateway

Common issues:
- Upstream 5xx spikes due to backend timeouts
- Rate limiting misconfiguration causing 429 bursts
- TLS certificate expiry leading to 502/526

Mitigations:
1. Verify upstream health checks and circuit breaker status
2. Increase upstream timeouts cautiously (validate with canary)
3. Adjust rate limits with temporary exceptions for critical paths
4. Rotate/renew TLS certificates and reload gateway

Diagnostics:
- Inspect error ratios by upstream service
- Check connection pool saturation and retry counts
- Correlate with deployment timelines of checkout-service and payment-service

Rollback:
- Revert gateway config change
- Disable newly added routes or features via flag


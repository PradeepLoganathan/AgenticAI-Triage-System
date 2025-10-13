# Order Service Runbook

Service: order-service

Symptoms:
- Degraded success rate with spikes in 5xx
- Increased latency calling inventory-service and payment-service

Common causes:
- Inventory timeouts under load
- Payment gateway intermittency
- Database deadlocks on order update workflow

Steps:
1. Check dependency health (inventory-service, payment-service)
2. Inspect order processing queue length and thread pool utilization
3. Verify recent deployments and feature flags
4. If deadlocks detected, reduce concurrency and apply retry with jitter

Rollback/mitigation:
- Scale out order-service replicas
- Temporarily disable non-critical enrichment steps
- Apply canary rollback if deployment related


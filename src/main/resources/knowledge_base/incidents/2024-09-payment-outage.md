# Incident Report: Payment Service Outage (P1)

Date: 2024-09-18
Service: payment-service
Version: v2.1.4 (deployed at 14:25Z)
Impact: 100% of payments failing; estimated $50K/hour revenue loss

Timeline
- 14:25Z: payment-service v2.1.4 deployed
- 14:28Z: Spike in 503 and database connection timeouts to payment-db-primary
- 14:30Z: Load balancer health checks failing for 3/6 instances
- 14:31Z: Error rate peaks at 28.7%; P99 latency 2.1s
- 14:33Z: Rollback initiated to v2.1.3
- 14:36Z: Error rate recovers below 2%

Root Cause
- Misconfigured DB connection pool settings reduced max connections under load, leading to connection starvation and cascading retries.

Remediation
- Rolled back to v2.1.3
- Restored connection pool settings; added canary validation step to post-deployment checklist

Prevention
- Add load test gate with DB connection budget validation
- Improve circuit breaker thresholds for DB operations

Keywords
payment-service, 503, database connection timeout, payment-db-primary, v2.1.4, rollback, load balancer health

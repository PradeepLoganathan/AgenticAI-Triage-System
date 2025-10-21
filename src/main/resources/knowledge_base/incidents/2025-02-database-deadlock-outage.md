# Incident: Database Deadlock Outage

Date: 2025-02-03 09:10 UTC
Services: payment-service, order-service, database

Summary:
Multiple services experienced write timeouts due to database-level deadlocks.

Findings:
- Long-running transaction in order-service caused lock contention
- payment-service retries amplified contention
- Deadlock rate peaked at 35/min, error rate exceeded 20%

Remediation:
- Killed offending transaction, reduced concurrency for write path
- Backed off retries with exponential backoff and jitter
- Temporarily disabled non-critical writes

Prevention:
- Add deadlock detection alarms
- Optimize transaction boundaries and indexes


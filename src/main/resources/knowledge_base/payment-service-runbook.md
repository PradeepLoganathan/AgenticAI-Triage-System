# Runbook: Payment Service

**Symptom**: High rate of 5xx errors and increased latency.

**Common Cause**: Database connection pool exhaustion.

**Diagnosis Steps**:
1. Check the `payment-service` logs for `ConnectionPoolTimeoutException`.
2. Query metrics for `database_connection_pool_active` and `database_connection_pool_pending`.

**Remediation Steps**:
1. **Immediate (Low-Risk)**: Restart the `payment-service` instances one by one to reset the connection pools. This is a temporary fix.
   - Command: `kubectl rollout restart deployment/payment-service`
2. **Long-Term (Requires Review)**: Increase the maximum size of the database connection pool in the service configuration.
   - Justification: This should be done after analyzing the load and confirming that the database can handle more connections.

# Operations Playbook: Database

Scope: Primary OLTP database backing payment-service, checkout-service, and user-service.

Symptoms
- High IO wait (>= 30%)
- Slow query spikes; lock contention; deadlocks
- Connection pool exhaustion in application services

Quick Checks
- CPU, IO wait, disk throughput
- Active connections vs. max
- Top slow queries (last 15m)
- Replication lag

Diagnostics
- Enable slow query log threshold (e.g., > 500ms); capture sample
- pg_stat_statements / performance schema to identify hotspots
- Check recent schema migrations

Remediation
1) Immediate
   - Kill runaway queries (lowest risk first)
   - Increase pool size slightly (5-10%) if DB has headroom
   - Scale read replicas for read-heavy paths
2) Short-term
   - Add indexes identified by analysis
   - Optimize slow queries (review execution plans)
3) Long-term
   - Partition large tables; archive cold data
   - Capacity planning and connection budget per service

Rollback
- Revert schema migration if safe; ensure backups and PITR windows

Keywords
database, slow query, io wait, connection pool, deadlock, migration, index, replication

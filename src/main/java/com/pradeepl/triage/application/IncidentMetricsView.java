package com.pradeepl.triage.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.Consume;
import akka.javasdk.annotations.DeleteHandler;
import akka.javasdk.annotations.Query;
import akka.javasdk.view.TableUpdater;
import akka.javasdk.view.View;

/**
 * IncidentMetricsView provides queryable view of all incidents for dashboards.
 *
 * Automatically builds and maintains a queryable table from IncidentMetrics entities.
 * Supports filtering by service, severity, status, etc.
 *
 * This is the recommended pattern for querying across multiple entities.
 */
@Component(id = "incident-metrics-view")
public class IncidentMetricsView extends View {

    @Consume.FromKeyValueEntity(IncidentMetrics.class)
    public static class IncidentMetricsUpdater extends TableUpdater<IncidentMetrics.IncidentRecord> {
        
        public Effect<IncidentMetrics.IncidentRecord> onUpdate(IncidentMetrics.IncidentRecord incident) {
            if (incident == null) {
                return effects().ignore();
            }
            return effects().updateRow(incident);
        }
    }

    @Query("SELECT * FROM incident_metrics_view")
    public QueryEffect<IncidentMetrics.IncidentRecord> getAllIncidents() {
        return queryResult();
    }

    @Query("SELECT * FROM incident_metrics_view WHERE isActive = true")
    public QueryEffect<IncidentMetrics.IncidentRecord> getActiveIncidents() {
        return queryResult();
    }

    @Query("SELECT * FROM incident_metrics_view WHERE service = :service")
    public QueryEffect<IncidentMetrics.IncidentRecord> getIncidentsByService(String service) {
        return queryResult();
    }

    @Query("SELECT * FROM incident_metrics_view WHERE severity = :severity")
    public QueryEffect<IncidentMetrics.IncidentRecord> getIncidentsBySeverity(String severity) {
        return queryResult();
    }

    @Query("SELECT * FROM incident_metrics_view WHERE severity = 'P1' AND isActive = true")
    public QueryEffect<IncidentMetrics.IncidentRecord> getCriticalIncidents() {
        return queryResult();
    }

    @Query("SELECT * FROM incident_metrics_view WHERE (severity = 'P1' OR requiresEscalation = true) AND isActive = true")
    public QueryEffect<IncidentMetrics.IncidentRecord> getCriticalOrEscalationIncidents() {
        return queryResult();
    }
}

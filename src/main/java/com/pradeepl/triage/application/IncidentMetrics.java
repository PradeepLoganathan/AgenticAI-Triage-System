package com.pradeepl.triage.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * IncidentMetrics stores metrics for a single incident.
 *
 * One entity instance per incident (keyed by incident/workflow ID).
 * State is automatically persisted and distributed by Akka.
 *
 * Updated by IncidentMetricsConsumer as workflows progress.
 * Queried via IncidentMetricsView for dashboard aggregations.
 */
@Component(id="incident-metrics")
public class IncidentMetrics extends KeyValueEntity<IncidentMetrics.IncidentRecord> {

    /**
     * Incident record - this is the entity state.
     */
    public record IncidentRecord(
        String incidentId,
        String status,
        String service,
        String severity,
        String title,
        Instant startTime,
        Instant lastUpdate,
        double overallConfidence,
        boolean requiresEscalation,
        int stepProgress,
        String assignedTeam,
        boolean isActive
    ) {
        @JsonCreator
        public IncidentRecord(
            @JsonProperty("incidentId") String incidentId,
            @JsonProperty("status") String status,
            @JsonProperty("service") String service,
            @JsonProperty("severity") String severity,
            @JsonProperty("title") String title,
            @JsonProperty("startTime") Instant startTime,
            @JsonProperty("lastUpdate") Instant lastUpdate,
            @JsonProperty("overallConfidence") double overallConfidence,
            @JsonProperty("requiresEscalation") boolean requiresEscalation,
            @JsonProperty("stepProgress") int stepProgress,
            @JsonProperty("assignedTeam") String assignedTeam,
            @JsonProperty("isActive") boolean isActive
        ) {
            this.incidentId = incidentId;
            this.status = status;
            this.service = service;
            this.severity = severity;
            this.title = title;
            this.startTime = startTime;
            this.lastUpdate = lastUpdate;
            this.overallConfidence = overallConfidence;
            this.requiresEscalation = requiresEscalation;
            this.stepProgress = stepProgress;
            this.assignedTeam = assignedTeam;
            this.isActive = isActive;
        }
    }

    /**
     * Update incident metrics.
     * Simply replaces the entire state - no manual list management needed!
     */
    public Effect<String> updateIncident(IncidentRecord incident) {
        return effects()
            .updateState(incident)
            .thenReply("Updated");
    }

    /**
     * Get current incident metrics.
     */
    public ReadOnlyEffect<IncidentRecord> getIncident() {
        return effects().reply(currentState());
    }
}

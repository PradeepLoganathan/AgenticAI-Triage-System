package com.pradeepl.triage.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * IncidentMetrics stores queryable metrics for incidents.
 *
 * This Key-Value Entity maintains:
 * - List of all incidents with their current state
 * - Searchable/filterable incident data
 * - Accessible via HTTP endpoints for dashboards
 *
 * Updated by IncidentMetricsConsumer as workflows progress.
 */
@Component(id="incident-metrics")
public class IncidentMetrics extends KeyValueEntity<IncidentMetrics.MetricsState> {

    /**
     * State holding all incident metrics.
     */
    public record MetricsState(
        List<IncidentRecord> incidents,
        LocalDateTime lastUpdated
    ) {
        @JsonCreator
        public MetricsState(
            @JsonProperty("incidents") List<IncidentRecord> incidents,
            @JsonProperty("lastUpdated") LocalDateTime lastUpdated
        ) {
            this.incidents = incidents != null ? incidents : new ArrayList<>();
            this.lastUpdated = lastUpdated != null ? lastUpdated : LocalDateTime.now();
        }

        public static MetricsState empty() {
            return new MetricsState(new ArrayList<>(), LocalDateTime.now());
        }
    }

    /**
     * Individual incident record.
     */
    public record IncidentRecord(
        String incidentId,
        String status,
        String service,
        String severity,
        String title,
        LocalDateTime startTime,
        LocalDateTime lastUpdate,
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
            @JsonProperty("startTime") LocalDateTime startTime,
            @JsonProperty("lastUpdate") LocalDateTime lastUpdate,
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
     * Command to add or update an incident.
     */
    public record UpdateIncident(IncidentRecord incident) {}

    /**
     * Command to get all incidents.
     */
    public record GetAllIncidents() {}

    public Effect<String> updateIncident(UpdateIncident cmd) {
        var state = currentState();
        if (state == null) {
            state = MetricsState.empty();
        }

        // Remove existing record if present
        var incidents = new ArrayList<>(state.incidents());
        incidents.removeIf(i -> i.incidentId().equals(cmd.incident().incidentId()));

        // Add updated record
        incidents.add(cmd.incident());

        var newState = new MetricsState(incidents, LocalDateTime.now());

        return effects()
            .updateState(newState)
            .thenReply("Updated");
    }

    public ReadOnlyEffect<MetricsState> getAllIncidents() {
        var state = currentState();
        if (state == null) {
            state = MetricsState.empty();
        }
        return effects().reply(state);
    }
}

package com.pradeepl.triage.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * IncidentRegistry maintains a central list of all incidents.
 *
 * Single entity (ID="global") that holds all incidents in memory.
 * Simple to query - just get the list, no streaming complexity.
 *
 * Perfect for dashboards with reasonable incident counts (< 10,000).
 */
@Component(id = "incident-registry")
public class IncidentRegistry extends KeyValueEntity<IncidentRegistry.RegistryState> {

    /**
     * State holds the list of all incidents.
     */
    public record RegistryState(
        List<IncidentMetrics.IncidentRecord> incidents
    ) {
        @JsonCreator
        public RegistryState(
            @JsonProperty("incidents") List<IncidentMetrics.IncidentRecord> incidents
        ) {
            this.incidents = incidents != null ? incidents : new ArrayList<>();
        }

        public static RegistryState empty() {
            return new RegistryState(new ArrayList<>());
        }
    }

    /**
     * Add or update an incident in the registry.
     * If incident already exists (by incidentId), update it.
     * Otherwise, add it to the list.
     */
    public Effect<String> updateIncident(IncidentMetrics.IncidentRecord incident) {
        var current = currentState();
        if (current == null) {
            current = RegistryState.empty();
        }

        // Create mutable copy of the list
        List<IncidentMetrics.IncidentRecord> updatedList = new ArrayList<>(current.incidents());

        // Remove existing incident if present
        updatedList.removeIf(i -> i.incidentId().equals(incident.incidentId()));

        // Add the new/updated incident
        updatedList.add(incident);

        var newState = new RegistryState(updatedList);

        return effects()
            .updateState(newState)
            .thenReply("Updated incident: " + incident.incidentId());
    }

    /**
     * Get all incidents.
     * Simple - just return the list!
     */
    public ReadOnlyEffect<List<IncidentMetrics.IncidentRecord>> getAllIncidents() {
        var current = currentState();
        if (current == null) {
            return effects().reply(List.of());
        }
        return effects().reply(current.incidents());
    }

    /**
     * Get active incidents only.
     */
    public ReadOnlyEffect<List<IncidentMetrics.IncidentRecord>> getActiveIncidents() {
        var current = currentState();
        if (current == null) {
            return effects().reply(List.of());
        }

        var activeIncidents = current.incidents().stream()
            .filter(IncidentMetrics.IncidentRecord::isActive)
            .toList();

        return effects().reply(activeIncidents);
    }

    /**
     * Get incidents by service.
     */
    public ReadOnlyEffect<List<IncidentMetrics.IncidentRecord>> getIncidentsByService(String service) {
        var current = currentState();
        if (current == null) {
            return effects().reply(List.of());
        }

        var filtered = current.incidents().stream()
            .filter(i -> service.equalsIgnoreCase(i.service()))
            .toList();

        return effects().reply(filtered);
    }

    /**
     * Get incidents by severity.
     */
    public ReadOnlyEffect<List<IncidentMetrics.IncidentRecord>> getIncidentsBySeverity(String severity) {
        var current = currentState();
        if (current == null) {
            return effects().reply(List.of());
        }

        var filtered = current.incidents().stream()
            .filter(i -> severity.equalsIgnoreCase(i.severity()))
            .toList();

        return effects().reply(filtered);
    }

    /**
     * Get critical incidents (P1 or requiring escalation).
     */
    public ReadOnlyEffect<List<IncidentMetrics.IncidentRecord>> getCriticalIncidents() {
        var current = currentState();
        if (current == null) {
            return effects().reply(List.of());
        }

        var critical = current.incidents().stream()
            .filter(i -> i.isActive() &&
                        ("P1".equalsIgnoreCase(i.severity()) || i.requiresEscalation()))
            .toList();

        return effects().reply(critical);
    }

    /**
     * Remove an incident from the registry.
     */
    public Effect<String> removeIncident(String incidentId) {
        var current = currentState();
        if (current == null) {
            return effects().reply("Registry is empty");
        }

        List<IncidentMetrics.IncidentRecord> updatedList = new ArrayList<>(current.incidents());
        boolean removed = updatedList.removeIf(i -> i.incidentId().equals(incidentId));

        if (removed) {
            var newState = new RegistryState(updatedList);
            return effects()
                .updateState(newState)
                .thenReply("Removed incident: " + incidentId);
        } else {
            return effects().reply("Incident not found: " + incidentId);
        }
    }
}

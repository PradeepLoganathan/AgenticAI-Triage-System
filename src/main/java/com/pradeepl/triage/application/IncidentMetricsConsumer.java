package com.pradeepl.triage.application;

import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.pradeepl.triage.domain.TriageState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * IncidentMetricsConsumer updates the incident metrics store as workflows progress.
 *
 * Listens to TriageWorkflow state changes and maintains a queryable
 * incident dashboard.
 */
@Consume.FromWorkflow(TriageWorkflow.class)
@akka.javasdk.annotations.Component(id="incident-metrics-consumer")
public class IncidentMetricsConsumer extends Consumer {

    private static final Logger logger = LoggerFactory.getLogger(IncidentMetricsConsumer.class);
    private static final String METRICS_ENTITY_ID = "global-metrics";

    private final ComponentClient componentClient;

    public IncidentMetricsConsumer(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public Effect onStateChanged(TriageState state) {
        if (state == null) {
            return effects().done();
        }

        // Get workflow ID from metadata
        String workflowId = messageContext().metadata().asCloudEvent().subject().orElse("unknown");
        
        logger.debug("Updating incident metrics for: {}", workflowId);

        // Extract incident details
        String service = extractService(state.classificationJson());
        String severity = extractSeverity(state.classificationJson());
        double confidence = 0.85; // Default
        boolean escalation = requiresEscalation(severity);
        int progress = calculateProgress(state.status());
        String title = extractTitle(state.incident());
        String team = determineTeam(service, severity);
        boolean isActive = state.status() != TriageState.Status.COMPLETED;

        var incidentRecord = new IncidentMetrics.IncidentRecord(
            workflowId,
            state.status().name(),
            service,
            severity,
            title,
            LocalDateTime.now(),
            LocalDateTime.now(),
            confidence,
            escalation,
            progress,
            team,
            isActive
        );

        // Update metrics entity (use global metrics ID for aggregated view)
        componentClient
            .forKeyValueEntity(METRICS_ENTITY_ID)
            .method(IncidentMetrics::updateIncident)
            .invoke(new IncidentMetrics.UpdateIncident(incidentRecord));

        logger.info("Updated metrics: incident={}, service={}, severity={}, progress={}/7",
                   workflowId, service, severity, progress);

        return effects().done();
    }

    private String extractService(String classificationJson) {
        if (classificationJson == null || classificationJson.isBlank()) {
            return "unknown";
        }
        if (classificationJson.contains("payment")) return "payment-service";
        if (classificationJson.contains("auth")) return "auth-service";
        if (classificationJson.contains("database")) return "database-service";
        return "platform-service";
    }

    private String extractSeverity(String classificationJson) {
        if (classificationJson == null || classificationJson.isBlank()) {
            return "P3";
        }
        if (classificationJson.contains("critical") || classificationJson.contains("P1")) return "P1";
        if (classificationJson.contains("high") || classificationJson.contains("P2")) return "P2";
        if (classificationJson.contains("P4")) return "P4";
        return "P3";
    }

    private boolean requiresEscalation(String severity) {
        return "P1".equals(severity);
    }

    private int calculateProgress(TriageState.Status status) {
        return switch (status) {
            case INITIATED -> 0;
            case PREPARED -> 1;
            case CLASSIFIED -> 2;
            case EVIDENCE_COLLECTED -> 3;
            case TRIAGED -> 4;
            case KNOWLEDGE_BASE_SEARCHED -> 5;
            case REMEDIATION_PROPOSED -> 6;
            case SUMMARY_READY, COMPLETED -> 7;
        };
    }

    private String extractTitle(String incident) {
        if (incident == null || incident.isBlank()) {
            return "Unknown incident";
        }
        String title = incident.substring(0, Math.min(100, incident.length()));
        return title.contains("\n") ? title.split("\n")[0] : title;
    }

    private String determineTeam(String service, String severity) {
        if ("P1".equals(severity)) {
            return "SRE-ONCALL";
        }
        return switch (service) {
            case "payment-service" -> "PAYMENTS-TEAM";
            case "auth-service" -> "SECURITY-TEAM";
            case "database-service" -> "DATABASE-TEAM";
            default -> "PLATFORM-TEAM";
        };
    }
}

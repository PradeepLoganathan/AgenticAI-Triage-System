package com.pradeepl.triage.application;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.util.stream.Collectors;

/**
 * IncidentDashboardEndpoint provides HTTP API for querying incident metrics.
 *
 * Endpoints:
 * - GET /dashboard/incidents - Get all incidents
 * - GET /dashboard/incidents/active - Get active incidents
 * - GET /dashboard/incidents/service/{service} - Get by service
 * - GET /dashboard/incidents/severity/{severity} - Get by severity
 * - GET /dashboard/incidents/critical - Get critical incidents
 * - GET /dashboard/stats - Get dashboard statistics
 */
@HttpEndpoint("/dashboard")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class IncidentDashboardEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(IncidentDashboardEndpoint.class);
    private static final String METRICS_ENTITY_ID = "global-metrics";

    private final ComponentClient componentClient;

    public IncidentDashboardEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    /**
     * Get all incidents.
     */
    @Get("/incidents")
    public HttpResponse getAllIncidents() {
        logger.info("Fetching all incidents");

        var result = componentClient
            .forKeyValueEntity(METRICS_ENTITY_ID)
            .method(IncidentMetrics::getAllIncidents)
            .invoke();

        return HttpResponses.ok(result);
    }

    /**
     * Get active (non-completed) incidents.
     */
    @Get("/incidents/active")
    public HttpResponse getActiveIncidents() {
        logger.info("Fetching active incidents");

        var state = componentClient
            .forKeyValueEntity(METRICS_ENTITY_ID)
            .method(IncidentMetrics::getAllIncidents)
            .invoke();

        var activeIncidents = state.incidents().stream()
                .filter(IncidentMetrics.IncidentRecord::isActive)
                .collect(Collectors.toList());

        return HttpResponses.ok(activeIncidents);
    }

    /**
     * Get incidents by service.
     */
    @Get("/incidents/service/{service}")
    public HttpResponse getIncidentsByService(String service) {
        logger.info("Fetching incidents for service: {}", service);

        var state = componentClient
            .forKeyValueEntity(METRICS_ENTITY_ID)
            .method(IncidentMetrics::getAllIncidents)
            .invoke();

        var incidents = state.incidents().stream()
                .filter(i -> service.equals(i.service()))
                .collect(Collectors.toList());

        return HttpResponses.ok(incidents);
    }

    /**
     * Get incidents by severity.
     */
    @Get("/incidents/severity/{severity}")
    public HttpResponse getIncidentsBySeverity(String severity) {
        logger.info("Fetching incidents with severity: {}", severity);

        var state = componentClient
            .forKeyValueEntity(METRICS_ENTITY_ID)
            .method(IncidentMetrics::getAllIncidents)
            .invoke();

        var incidents = state.incidents().stream()
                .filter(i -> severity.equals(i.severity()))
                .collect(Collectors.toList());

        return HttpResponses.ok(incidents);
    }

    /**
     * Get critical incidents (P1 or requiring escalation).
     */
    @Get("/incidents/critical")
    public HttpResponse getCriticalIncidents() {
        logger.info("Fetching critical incidents");

        var state = componentClient
            .forKeyValueEntity(METRICS_ENTITY_ID)
            .method(IncidentMetrics::getAllIncidents)
            .invoke();

        var criticalIncidents = state.incidents().stream()
                .filter(i -> "P1".equals(i.severity()) || i.requiresEscalation())
                .filter(IncidentMetrics.IncidentRecord::isActive)
                .collect(Collectors.toList());

        return HttpResponses.ok(criticalIncidents);
    }

    /**
     * Get dashboard statistics.
     */
    @Get("/stats")
    public HttpResponse getStats() {
        logger.info("Fetching dashboard statistics");

        var state = componentClient
            .forKeyValueEntity(METRICS_ENTITY_ID)
            .method(IncidentMetrics::getAllIncidents)
            .invoke();

        var incidents = state.incidents();
        long total = incidents.size();
        long active = incidents.stream().filter(IncidentMetrics.IncidentRecord::isActive).count();
        long p1 = incidents.stream().filter(i -> "P1".equals(i.severity())).count();
        long p2 = incidents.stream().filter(i -> "P2".equals(i.severity())).count();
        long escalations = incidents.stream().filter(IncidentMetrics.IncidentRecord::requiresEscalation).count();
        double avgProgress = incidents.stream()
            .mapToInt(IncidentMetrics.IncidentRecord::stepProgress)
            .average()
            .orElse(0.0);

        var stats = new DashboardStats(total, active, p1, p2, escalations, avgProgress);

        return HttpResponses.ok(stats);
    }

    public record DashboardStats(
        long totalIncidents,
        long activeIncidents,
        long p1Count,
        long p2Count,
        long escalationCount,
        double averageProgress
    ) {}
}

package com.pradeepl.triage.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.pradeepl.triage.application.IncidentMetricsView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

        var incidents = componentClient
            .forView()
            .stream(IncidentMetricsView::getAllIncidents)
            .source();

        return HttpResponses.ok(incidents);
    }

    /**
     * Get active (non-completed) incidents.
     */
    @Get("/incidents/active")
    public HttpResponse getActiveIncidents() {
        logger.info("Fetching active incidents");

        var incidents = componentClient
            .forView()
            .stream(IncidentMetricsView::getActiveIncidents)
            .source();

        return HttpResponses.ok(incidents);
    }

    /**
     * Get incidents by service.
     */
    @Get("/incidents/service/{service}")
    public HttpResponse getIncidentsByService(String service) {
        logger.info("Fetching incidents for service: {}", service);

        var incidents = componentClient
            .forView()
            .stream(IncidentMetricsView::getIncidentsByService)
            .source(service);

        return HttpResponses.ok(incidents);
    }

    /**
     * Get incidents by severity.
     */
    @Get("/incidents/severity/{severity}")
    public HttpResponse getIncidentsBySeverity(String severity) {
        logger.info("Fetching incidents with severity: {}", severity);

        var incidents = componentClient
            .forView()
            .stream(IncidentMetricsView::getIncidentsBySeverity)
            .source(severity);

        return HttpResponses.ok(incidents);
    }

    /**
     * Get critical incidents (P1 or requiring escalation).
     */
    @Get("/incidents/critical")
    public HttpResponse getCriticalIncidents() {
        logger.info("Fetching critical incidents");

        var incidents = componentClient
            .forView()
            .stream(IncidentMetricsView::getCriticalOrEscalationIncidents)
            .source();

        return HttpResponses.ok(incidents);
    }

    /**
     * Get dashboard statistics.
     * Note: For now, use /dashboard/incidents to get all incidents and calculate stats client-side.
     * TODO: Implement aggregation query in view for better performance.
     */
    @Get("/stats")
    public HttpResponse getStats() {
        logger.info("Fetching dashboard statistics");

        // Return placeholder stats - clients should use /dashboard/incidents for now
        var stats = new DashboardStats(0, 0, 0, 0, 0, 0.0);

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

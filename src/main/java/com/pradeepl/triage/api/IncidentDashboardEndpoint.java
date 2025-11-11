package com.pradeepl.triage.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.HttpResponses;
import com.pradeepl.triage.application.IncidentMetrics;
import com.pradeepl.triage.application.IncidentRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * IncidentDashboardEndpoint provides HTTP API for querying incident metrics.
 *
 * Uses IncidentRegistry (single entity with all incidents) for simple querying.
 * No streaming, no views, no complexity - just simple List returns!
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
     * Simple query to the central registry - returns a plain List!
     */
    @Get("/incidents")
    public HttpResponse getAllIncidents() {
        logger.info("Fetching all incidents");

        try {
            var incidents = componentClient
                .forKeyValueEntity("global")
                .method(IncidentRegistry::getAllIncidents)
                .invoke();

            logger.info("Returning {} incidents", incidents.size());
            return HttpResponses.ok(incidents);

        } catch (Exception e) {
            logger.error("Error fetching incidents", e);
            return HttpResponses.ok(List.of());
        }
    }

    /**
     * Get active (non-completed) incidents.
     */
    @Get("/incidents/active")
    public HttpResponse getActiveIncidents() {
        logger.info("Fetching active incidents");

        try {
            var incidents = componentClient
                .forKeyValueEntity("global")
                .method(IncidentRegistry::getActiveIncidents)
                .invoke();

            return HttpResponses.ok(incidents);
        } catch (Exception e) {
            logger.error("Error fetching active incidents", e);
            return HttpResponses.ok(List.of());
        }
    }

    /**
     * Get incidents by service.
     */
    @Get("/incidents/service/{service}")
    public HttpResponse getIncidentsByService(String service) {
        logger.info("Fetching incidents for service: {}", service);

        try {
            var incidents = componentClient
                .forKeyValueEntity("global")
                .method(IncidentRegistry::getIncidentsByService)
                .invoke(service);

            return HttpResponses.ok(incidents);
        } catch (Exception e) {
            logger.error("Error fetching incidents for service: {}", service, e);
            return HttpResponses.ok(List.of());
        }
    }

    /**
     * Get incidents by severity.
     */
    @Get("/incidents/severity/{severity}")
    public HttpResponse getIncidentsBySeverity(String severity) {
        logger.info("Fetching incidents with severity: {}", severity);

        try {
            var incidents = componentClient
                .forKeyValueEntity("global")
                .method(IncidentRegistry::getIncidentsBySeverity)
                .invoke(severity);

            return HttpResponses.ok(incidents);
        } catch (Exception e) {
            logger.error("Error fetching incidents with severity: {}", severity, e);
            return HttpResponses.ok(List.of());
        }
    }

    /**
     * Get critical incidents (P1 or requiring escalation).
     */
    @Get("/incidents/critical")
    public HttpResponse getCriticalIncidents() {
        logger.info("Fetching critical incidents");

        try {
            var incidents = componentClient
                .forKeyValueEntity("global")
                .method(IncidentRegistry::getCriticalIncidents)
                .invoke();

            return HttpResponses.ok(incidents);
        } catch (Exception e) {
            logger.error("Error fetching critical incidents", e);
            return HttpResponses.ok(List.of());
        }
    }

    /**
     * Get dashboard statistics.
     * Queries all incidents and calculates aggregate statistics.
     */
    @Get("/stats")
    public HttpResponse getStats() {
        logger.info("Fetching dashboard statistics");

        try {
            // Get all incidents from the registry
            var incidents = componentClient
                .forKeyValueEntity("global")
                .method(IncidentRegistry::getAllIncidents)
                .invoke();

            logger.info("Calculating stats from {} incidents", incidents.size());

            long totalIncidents = incidents.size();
            long activeIncidents = incidents.stream().filter(i -> i.isActive()).count();
            long p1Count = incidents.stream()
                .filter(i -> "P1".equalsIgnoreCase(i.severity()) && i.isActive())
                .count();
            long p2Count = incidents.stream()
                .filter(i -> "P2".equalsIgnoreCase(i.severity()) && i.isActive())
                .count();
            long escalationCount = incidents.stream()
                .filter(i -> i.requiresEscalation() && i.isActive())
                .count();

            double averageProgress = incidents.isEmpty() ? 0.0 :
                incidents.stream()
                    .filter(i -> i.isActive())
                    .mapToInt(i -> i.stepProgress())
                    .average()
                    .orElse(0.0);

            var stats = new DashboardStats(
                totalIncidents,
                activeIncidents,
                p1Count,
                p2Count,
                escalationCount,
                averageProgress
            );

            logger.info("Stats calculated: total={}, active={}, P1={}, P2={}, escalations={}, avgProgress={}",
                totalIncidents, activeIncidents, p1Count, p2Count, escalationCount, averageProgress);

            return HttpResponses.ok(stats);

        } catch (Exception e) {
            logger.error("Error calculating dashboard stats", e);
            // Return zeros on error rather than failing
            var stats = new DashboardStats(0, 0, 0, 0, 0, 0.0);
            return HttpResponses.ok(stats);
        }
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

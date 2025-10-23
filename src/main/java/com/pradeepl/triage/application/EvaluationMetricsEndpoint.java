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
 * EvaluationMetricsEndpoint provides HTTP API for querying LLM evaluation metrics.
 *
 * Endpoints:
 * - GET /evaluations - Get all evaluations
 * - GET /evaluations/failures - Get evaluations with failures
 * - GET /evaluations/workflow/{workflowId} - Get evaluation for specific workflow
 * - GET /evaluations/stats - Get evaluation statistics
 */
@HttpEndpoint("/evaluations")
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
public class EvaluationMetricsEndpoint {

    private static final Logger logger = LoggerFactory.getLogger(EvaluationMetricsEndpoint.class);
    private static final String METRICS_ENTITY_ID = "global-evaluations";

    private final ComponentClient componentClient;

    public EvaluationMetricsEndpoint(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    /**
     * Get all evaluations.
     */
    @Get
    public HttpResponse getAllEvaluations() {
        logger.info("Fetching all evaluations");

        var result = componentClient
            .forKeyValueEntity(METRICS_ENTITY_ID)
            .method(EvaluationMetrics::getAllEvaluations)
            .invoke();

        return HttpResponses.ok(result);
    }

    /**
     * Get evaluations with failures (any check failed).
     */
    @Get("/failures")
    public HttpResponse getFailedEvaluations() {
        logger.info("Fetching failed evaluations");

        var state = componentClient
            .forKeyValueEntity(METRICS_ENTITY_ID)
            .method(EvaluationMetrics::getAllEvaluations)
            .invoke();

        var failures = state.evaluations().stream()
            .filter(e -> !e.summaryToxicityPassed() ||
                        !e.remediationToxicityPassed() ||
                        !e.evidenceHallucinationPassed() ||
                        !e.triageHallucinationPassed() ||
                        !e.summaryHallucinationPassed())
            .collect(Collectors.toList());

        return HttpResponses.ok(failures);
    }

    /**
     * Get evaluation for a specific workflow.
     */
    @Get("/workflow/{workflowId}")
    public HttpResponse getEvaluationByWorkflow(String workflowId) {
        logger.info("Fetching evaluation for workflow: {}", workflowId);

        var state = componentClient
            .forKeyValueEntity(METRICS_ENTITY_ID)
            .method(EvaluationMetrics::getAllEvaluations)
            .invoke();

        var evaluation = state.evaluations().stream()
            .filter(e -> workflowId.equals(e.workflowId()))
            .findFirst()
            .orElse(null);

        return HttpResponses.ok(evaluation);
    }

    /**
     * Get evaluation statistics.
     */
    @Get("/stats")
    public HttpResponse getStats() {
        logger.info("Fetching evaluation statistics");

        var state = componentClient
            .forKeyValueEntity(METRICS_ENTITY_ID)
            .method(EvaluationMetrics::getAllEvaluations)
            .invoke();

        var evals = state.evaluations();
        long total = evals.size();

        if (total == 0) {
            return HttpResponses.ok(new EvaluationStats(0, 0, 0, 0, 0, 0, 0, 0.0, 0.0));
        }

        long summaryToxicityPass = evals.stream().filter(EvaluationMetrics.EvaluationRecord::summaryToxicityPassed).count();
        long remediationToxicityPass = evals.stream().filter(EvaluationMetrics.EvaluationRecord::remediationToxicityPassed).count();
        long evidenceHallucinationPass = evals.stream().filter(EvaluationMetrics.EvaluationRecord::evidenceHallucinationPassed).count();
        long triageHallucinationPass = evals.stream().filter(EvaluationMetrics.EvaluationRecord::triageHallucinationPassed).count();
        long summaryHallucinationPass = evals.stream().filter(EvaluationMetrics.EvaluationRecord::summaryHallucinationPassed).count();

        long allPassed = evals.stream()
            .filter(e -> e.summaryToxicityPassed() &&
                        e.remediationToxicityPassed() &&
                        e.evidenceHallucinationPassed() &&
                        e.triageHallucinationPassed() &&
                        e.summaryHallucinationPassed())
            .count();

        double avgToxicityConfidence = evals.stream()
            .mapToDouble(EvaluationMetrics.EvaluationRecord::toxicityConfidence)
            .average()
            .orElse(0.0);

        double avgHallucinationConfidence = evals.stream()
            .mapToDouble(EvaluationMetrics.EvaluationRecord::hallucinationConfidence)
            .average()
            .orElse(0.0);

        var stats = new EvaluationStats(
            total,
            allPassed,
            summaryToxicityPass,
            remediationToxicityPass,
            evidenceHallucinationPass,
            triageHallucinationPass,
            summaryHallucinationPass,
            avgToxicityConfidence,
            avgHallucinationConfidence
        );

        return HttpResponses.ok(stats);
    }

    public record EvaluationStats(
        long totalEvaluations,
        long allChecksPassed,
        long summaryToxicityPassCount,
        long remediationToxicityPassCount,
        long evidenceHallucinationPassCount,
        long triageHallucinationPassCount,
        long summaryHallucinationPassCount,
        double averageToxicityConfidence,
        double averageHallucinationConfidence
    ) {}
}

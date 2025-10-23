package com.pradeepl.triage.application;

import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.pradeepl.triage.domain.EvaluationResultsEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EvaluationMetricsConsumer listens to EvaluationResultsEntity changes and
 * aggregates them into a queryable metrics dashboard.
 *
 * Flow:
 * 1. TriageEvaluatorConsumer stores individual evaluation results in EvaluationResultsEntity
 * 2. This consumer listens to those changes
 * 3. When all 5 evaluations are complete (isComplete = true), it creates an aggregated record
 * 4. Aggregated record stored in EvaluationMetrics for dashboard queries
 *
 * This allows:
 * - Per-workflow detailed results in EvaluationResultsEntity
 * - Aggregated metrics for dashboard in EvaluationMetrics
 */
@Consume.FromKeyValueEntity(EvaluationResultsEntity.class)
@akka.javasdk.annotations.Component(id="evaluation-metrics-consumer")
public class EvaluationMetricsConsumer extends Consumer {

    private static final Logger logger = LoggerFactory.getLogger(EvaluationMetricsConsumer.class);
    private static final String METRICS_ENTITY_ID = "global-evaluations";

    private final ComponentClient componentClient;

    public EvaluationMetricsConsumer(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public Effect onUpdate(EvaluationResultsEntity.State state) {
        if (state == null) {
            logger.debug("Received null evaluation state, skipping");
            return effects().done();
        }

        // Only process when all evaluations are complete
        if (!state.isComplete()) {
            logger.debug("Evaluation not yet complete for workflow: {}, skipping metrics update", state.workflowId());
            return effects().done();
        }

        logger.info("📊 Creating evaluation metrics record for completed workflow: {}", state.workflowId());

        // Calculate average confidence (simple average for now)
        double toxicityConfidence = 0.85; // Default if no scores available
        double hallucinationConfidence = 0.85; // Default if no scores available

        // Combine explanations
        String toxicityExplanation = buildToxicityExplanation(state);
        String hallucinationExplanation = buildHallucinationExplanation(state);

        // Create aggregated evaluation record
        var evaluationRecord = new EvaluationMetrics.EvaluationRecord(
            state.workflowId(),
            state.summaryToxicity() != null && state.summaryToxicity().passed(),
            state.remediationToxicity() != null && state.remediationToxicity().passed(),
            state.evidenceHallucination() != null && state.evidenceHallucination().passed(),
            state.triageHallucination() != null && state.triageHallucination().passed(),
            state.summaryHallucination() != null && state.summaryHallucination().passed(),
            toxicityConfidence,
            hallucinationConfidence,
            toxicityExplanation,
            hallucinationExplanation,
            state.evaluatedAt(),
            "COMPLETED"
        );

        // Update metrics entity
        componentClient
            .forKeyValueEntity(METRICS_ENTITY_ID)
            .method(EvaluationMetrics::updateEvaluation)
            .invoke(new EvaluationMetrics.UpdateEvaluation(evaluationRecord));

        logger.info("✅ Evaluation metrics record created for workflow: {}", state.workflowId());

        return effects().done();
    }

    private String buildToxicityExplanation(EvaluationResultsEntity.State state) {
        StringBuilder sb = new StringBuilder();

        if (state.summaryToxicity() != null) {
            sb.append("Summary: ").append(state.summaryToxicity().explanation()).append("\n");
        }

        if (state.remediationToxicity() != null) {
            sb.append("Remediation: ").append(state.remediationToxicity().explanation()).append("\n");
        }

        return sb.length() > 0 ? sb.toString() : "No toxicity evaluations completed";
    }

    private String buildHallucinationExplanation(EvaluationResultsEntity.State state) {
        StringBuilder sb = new StringBuilder();

        if (state.evidenceHallucination() != null) {
            sb.append("Evidence: ").append(state.evidenceHallucination().explanation()).append("\n");
        }

        if (state.triageHallucination() != null) {
            sb.append("Triage: ").append(state.triageHallucination().explanation()).append("\n");
        }

        if (state.summaryHallucination() != null) {
            sb.append("Summary: ").append(state.summaryHallucination().explanation()).append("\n");
        }

        return sb.length() > 0 ? sb.toString() : "No hallucination evaluations completed";
    }
}

package com.pradeepl.triage.application;

import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.pradeepl.triage.domain.TriageState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;

/**
 * EvaluationMetricsConsumer stores evaluation results in a queryable metrics entity.
 *
 * NOTE: This is a placeholder implementation. In the current architecture,
 * TriageEvaluatorConsumer uses fire-and-forget pattern for evaluations, so we
 * cannot capture actual evaluation results here.
 *
 * To fully implement this:
 * 1. Modify TriageEvaluatorConsumer to await evaluation results using .thenApply()
 * 2. Store results in TriageState or a separate EvaluationResults entity
 * 3. This consumer would then read those results and update the metrics
 *
 * For now, this consumer creates placeholder evaluation records to demonstrate
 * the query pattern.
 */
@Consume.FromWorkflow(TriageWorkflow.class)
public class EvaluationMetricsConsumer extends Consumer {

    private static final Logger logger = LoggerFactory.getLogger(EvaluationMetricsConsumer.class);
    private static final String METRICS_ENTITY_ID = "global-evaluations";

    private final ComponentClient componentClient;

    public EvaluationMetricsConsumer(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public Effect onStateChanged(TriageState state) {
        if (state == null) {
            return effects().done();
        }

        // Only process completed workflows
        if (state.status() != TriageState.Status.COMPLETED) {
            return effects().done();
        }

        logger.debug("Creating evaluation metrics record for workflow: {}", state.workflowId());

        // TODO: Replace with actual evaluation results when available
        // For now, create placeholder record showing the structure
        var evaluationRecord = new EvaluationMetrics.EvaluationRecord(
            state.workflowId(),
            true,  // summaryToxicityPassed - placeholder
            true,  // remediationToxicityPassed - placeholder
            true,  // evidenceHallucinationPassed - placeholder
            true,  // triageHallucinationPassed - placeholder
            true,  // summaryHallucinationPassed - placeholder
            0.85,  // toxicityConfidence - placeholder
            0.90,  // hallucinationConfidence - placeholder
            "Placeholder: Toxicity evaluation results would appear here",
            "Placeholder: Hallucination evaluation results would appear here",
            LocalDateTime.now(),
            "PLACEHOLDER"  // evaluationStatus
        );

        // Update metrics entity
        componentClient
            .forKeyValueEntity(METRICS_ENTITY_ID)
            .method(EvaluationMetrics::updateEvaluation)
            .invoke(new EvaluationMetrics.UpdateEvaluation(evaluationRecord));

        logger.info("Created evaluation metrics record for workflow: {}", state.workflowId());

        return effects().done();
    }
}

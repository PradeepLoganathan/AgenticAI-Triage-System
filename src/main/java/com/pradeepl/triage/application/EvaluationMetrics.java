package com.pradeepl.triage.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * EvaluationMetrics stores queryable LLM evaluation metrics.
 *
 * This Key-Value Entity maintains:
 * - Evaluation results for toxicity and hallucination checks
 * - Pass/fail rates and confidence scores
 * - Detailed explanations for each evaluation
 * - Accessible via HTTP endpoints for monitoring dashboards
 *
 * Updated by TriageEvaluatorConsumer as evaluations complete.
 */
@Component(id="evaluation-metrics")
public class EvaluationMetrics extends KeyValueEntity<EvaluationMetrics.MetricsState> {

    /**
     * State holding all evaluation metrics.
     */
    public record MetricsState(
        List<EvaluationRecord> evaluations,
        LocalDateTime lastUpdated
    ) {
        @JsonCreator
        public MetricsState(
            @JsonProperty("evaluations") List<EvaluationRecord> evaluations,
            @JsonProperty("lastUpdated") LocalDateTime lastUpdated
        ) {
            this.evaluations = evaluations != null ? evaluations : new ArrayList<>();
            this.lastUpdated = lastUpdated != null ? lastUpdated : LocalDateTime.now();
        }

        public static MetricsState empty() {
            return new MetricsState(new ArrayList<>(), LocalDateTime.now());
        }
    }

    /**
     * Individual evaluation record for a workflow run.
     */
    public record EvaluationRecord(
        String workflowId,
        boolean summaryToxicityPassed,
        boolean remediationToxicityPassed,
        boolean evidenceHallucinationPassed,
        boolean triageHallucinationPassed,
        boolean summaryHallucinationPassed,
        double toxicityConfidence,
        double hallucinationConfidence,
        String toxicityExplanation,
        String hallucinationExplanation,
        LocalDateTime evaluatedAt,
        String evaluationStatus
    ) {
        @JsonCreator
        public EvaluationRecord(
            @JsonProperty("workflowId") String workflowId,
            @JsonProperty("summaryToxicityPassed") boolean summaryToxicityPassed,
            @JsonProperty("remediationToxicityPassed") boolean remediationToxicityPassed,
            @JsonProperty("evidenceHallucinationPassed") boolean evidenceHallucinationPassed,
            @JsonProperty("triageHallucinationPassed") boolean triageHallucinationPassed,
            @JsonProperty("summaryHallucinationPassed") boolean summaryHallucinationPassed,
            @JsonProperty("toxicityConfidence") double toxicityConfidence,
            @JsonProperty("hallucinationConfidence") double hallucinationConfidence,
            @JsonProperty("toxicityExplanation") String toxicityExplanation,
            @JsonProperty("hallucinationExplanation") String hallucinationExplanation,
            @JsonProperty("evaluatedAt") LocalDateTime evaluatedAt,
            @JsonProperty("evaluationStatus") String evaluationStatus
        ) {
            this.workflowId = workflowId;
            this.summaryToxicityPassed = summaryToxicityPassed;
            this.remediationToxicityPassed = remediationToxicityPassed;
            this.evidenceHallucinationPassed = evidenceHallucinationPassed;
            this.triageHallucinationPassed = triageHallucinationPassed;
            this.summaryHallucinationPassed = summaryHallucinationPassed;
            this.toxicityConfidence = toxicityConfidence;
            this.hallucinationConfidence = hallucinationConfidence;
            this.toxicityExplanation = toxicityExplanation;
            this.hallucinationExplanation = hallucinationExplanation;
            this.evaluatedAt = evaluatedAt;
            this.evaluationStatus = evaluationStatus;
        }
    }

    /**
     * Command to add or update an evaluation.
     */
    public record UpdateEvaluation(EvaluationRecord evaluation) {}

    /**
     * Command to get all evaluations.
     */
    public record GetAllEvaluations() {}

    public Effect<String> updateEvaluation(UpdateEvaluation cmd) {
        var state = currentState();
        if (state == null) {
            state = MetricsState.empty();
        }

        // Remove existing record if present
        var evaluations = new ArrayList<>(state.evaluations());
        evaluations.removeIf(e -> e.workflowId().equals(cmd.evaluation().workflowId()));

        // Add updated record
        evaluations.add(cmd.evaluation());

        var newState = new MetricsState(evaluations, LocalDateTime.now());

        return effects()
            .updateState(newState)
            .thenReply("Updated");
    }

    public ReadOnlyEffect<MetricsState> getAllEvaluations() {
        var state = currentState();
        if (state == null) {
            state = MetricsState.empty();
        }
        return effects().reply(state);
    }
}

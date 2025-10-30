package com.pradeepl.triage.domain;

import akka.javasdk.annotations.Component;
import akka.javasdk.keyvalueentity.KeyValueEntity;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * EvaluationResultsEntity stores the actual evaluation results from built-in evaluators.
 *
 * This entity is keyed by workflow ID and stores the results of:
 * - Toxicity evaluations (summary, remediation)
 * - Hallucination evaluations (evidence, triage, summary)
 *
 * Updated by TriageEvaluatorConsumer as evaluations complete.
 * Read by EvaluationMetricsConsumer to aggregate into metrics dashboard.
 */
@Component(id = "evaluation-results-entity")
public class EvaluationResultsEntity extends KeyValueEntity<EvaluationResultsEntity.State> {

    /**
     * Complete evaluation results for a single workflow run.
     */
    public record State(
        String workflowId,
        ToxicityResult summaryToxicity,
        ToxicityResult remediationToxicity,
        HallucinationResult evidenceHallucination,
        HallucinationResult triageHallucination,
        HallucinationResult summaryHallucination,
        LocalDateTime evaluatedAt,
        boolean isComplete
    ) {
        @JsonCreator
        public State(
            @JsonProperty("workflowId") String workflowId,
            @JsonProperty("summaryToxicity") ToxicityResult summaryToxicity,
            @JsonProperty("remediationToxicity") ToxicityResult remediationToxicity,
            @JsonProperty("evidenceHallucination") HallucinationResult evidenceHallucination,
            @JsonProperty("triageHallucination") HallucinationResult triageHallucination,
            @JsonProperty("summaryHallucination") HallucinationResult summaryHallucination,
            @JsonProperty("evaluatedAt") LocalDateTime evaluatedAt,
            @JsonProperty("isComplete") boolean isComplete
        ) {
            this.workflowId = workflowId;
            this.summaryToxicity = summaryToxicity;
            this.remediationToxicity = remediationToxicity;
            this.evidenceHallucination = evidenceHallucination;
            this.triageHallucination = triageHallucination;
            this.summaryHallucination = summaryHallucination;
            this.evaluatedAt = evaluatedAt;
            this.isComplete = isComplete;
        }

        public static State empty(String workflowId) {
            return new State(workflowId, null, null, null, null, null, LocalDateTime.now(), false);
        }
    }

    /**
     * Result of a toxicity evaluation.
     */
    public record ToxicityResult(
        boolean passed,
        String explanation
    ) {
        @JsonCreator
        public ToxicityResult(
            @JsonProperty("passed") boolean passed,
            @JsonProperty("explanation") String explanation
        ) {
            this.passed = passed;
            this.explanation = explanation;
        }
    }

    /**
     * Result of a hallucination evaluation.
     */
    public record HallucinationResult(
        boolean passed,
        String explanation
    ) {
        @JsonCreator
        public HallucinationResult(
            @JsonProperty("passed") boolean passed,
            @JsonProperty("explanation") String explanation
        ) {
            this.passed = passed;
            this.explanation = explanation;
        }
    }

    // Commands

    public record RecordSummaryToxicity(ToxicityResult result) {}
    public record RecordRemediationToxicity(ToxicityResult result) {}
    public record RecordEvidenceHallucination(HallucinationResult result) {}
    public record RecordTriageHallucination(HallucinationResult result) {}
    public record RecordSummaryHallucination(HallucinationResult result) {}

    // Command handlers

    public Effect<String> recordSummaryToxicity(RecordSummaryToxicity cmd) {
        var state = currentState();
        if (state == null) {
            state = State.empty(commandContext().entityId());
        }

        var newState = new State(
            state.workflowId(),
            cmd.result(),
            state.remediationToxicity(),
            state.evidenceHallucination(),
            state.triageHallucination(),
            state.summaryHallucination(),
            LocalDateTime.now(),
            checkComplete(cmd.result(), state.remediationToxicity(), state.evidenceHallucination(),
                         state.triageHallucination(), state.summaryHallucination())
        );

        return effects()
            .updateState(newState)
            .thenReply("Recorded");
    }

    public Effect<String> recordRemediationToxicity(RecordRemediationToxicity cmd) {
        var state = currentState();
        if (state == null) {
            state = State.empty(commandContext().entityId());
        }

        var newState = new State(
            state.workflowId(),
            state.summaryToxicity(),
            cmd.result(),
            state.evidenceHallucination(),
            state.triageHallucination(),
            state.summaryHallucination(),
            LocalDateTime.now(),
            checkComplete(state.summaryToxicity(), cmd.result(), state.evidenceHallucination(),
                         state.triageHallucination(), state.summaryHallucination())
        );

        return effects()
            .updateState(newState)
            .thenReply("Recorded");
    }

    public Effect<String> recordEvidenceHallucination(RecordEvidenceHallucination cmd) {
        var state = currentState();
        if (state == null) {
            state = State.empty(commandContext().entityId());
        }

        var newState = new State(
            state.workflowId(),
            state.summaryToxicity(),
            state.remediationToxicity(),
            cmd.result(),
            state.triageHallucination(),
            state.summaryHallucination(),
            LocalDateTime.now(),
            checkComplete(state.summaryToxicity(), state.remediationToxicity(), cmd.result(),
                         state.triageHallucination(), state.summaryHallucination())
        );

        return effects()
            .updateState(newState)
            .thenReply("Recorded");
    }

    public Effect<String> recordTriageHallucination(RecordTriageHallucination cmd) {
        var state = currentState();
        if (state == null) {
            state = State.empty(commandContext().entityId());
        }

        var newState = new State(
            state.workflowId(),
            state.summaryToxicity(),
            state.remediationToxicity(),
            state.evidenceHallucination(),
            cmd.result(),
            state.summaryHallucination(),
            LocalDateTime.now(),
            checkComplete(state.summaryToxicity(), state.remediationToxicity(), state.evidenceHallucination(),
                         cmd.result(), state.summaryHallucination())
        );

        return effects()
            .updateState(newState)
            .thenReply("Recorded");
    }

    public Effect<String> recordSummaryHallucination(RecordSummaryHallucination cmd) {
        var state = currentState();
        if (state == null) {
            state = State.empty(commandContext().entityId());
        }

        var newState = new State(
            state.workflowId(),
            state.summaryToxicity(),
            state.remediationToxicity(),
            state.evidenceHallucination(),
            state.triageHallucination(),
            cmd.result(),
            LocalDateTime.now(),
            checkComplete(state.summaryToxicity(), state.remediationToxicity(), state.evidenceHallucination(),
                         state.triageHallucination(), cmd.result())
        );

        return effects()
            .updateState(newState)
            .thenReply("Recorded");
    }

    public ReadOnlyEffect<State> getResults() {
        var state = currentState();
        if (state == null) {
            state = State.empty(commandContext().entityId());
        }
        return effects().reply(state);
    }

    /**
     * Get current state (for debugging/monitoring).
     */
    public ReadOnlyEffect<State> getState() {
        var state = currentState();
        if (state == null) {
            state = State.empty(commandContext().entityId());
        }
        return effects().reply(state);
    }

    /**
     * Check if all 5 evaluations are complete.
     */
    private boolean checkComplete(ToxicityResult summaryTox, ToxicityResult remediationTox,
                                  HallucinationResult evidenceHal, HallucinationResult triageHal,
                                  HallucinationResult summaryHal) {
        return summaryTox != null &&
               remediationTox != null &&
               evidenceHal != null &&
               triageHal != null &&
               summaryHal != null;
    }
}

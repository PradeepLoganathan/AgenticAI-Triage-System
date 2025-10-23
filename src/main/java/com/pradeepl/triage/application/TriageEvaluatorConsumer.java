package com.pradeepl.triage.application;

import akka.javasdk.annotations.Consume;
import akka.javasdk.agent.evaluator.HallucinationEvaluator;
import akka.javasdk.agent.evaluator.ToxicityEvaluator;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import com.pradeepl.triage.domain.TriageState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TriageEvaluatorConsumer listens to TriageWorkflow state changes and
 * evaluates outputs for toxicity and hallucinations.
 *
 * Uses Akka SDK's built-in evaluators:
 * - ToxicityEvaluator: Assesses hateful, inappropriate, or toxic content
 * - HallucinationEvaluator: Detects unsupported facts vs reference text
 *
 * This follows the Akka SDK pattern for LLM evaluation:
 * - Runs evaluations after workflow completion in the consumer
 * - Consumer runs in background, does not block the workflow response
 * - Results captured and stored in EvaluationResultsEntity
 * - Results available for analytics and quality monitoring via dashboards
 *
 * Flow:
 * 1. Workflow completes
 * 2. This consumer triggers 5 evaluations (synchronous within consumer)
 * 3. Each evaluation stores results in EvaluationResultsEntity (keyed by workflow ID)
 * 4. EvaluationMetricsConsumer aggregates results into metrics dashboard
 *
 * NOTE: In production, consider disabling this consumer to avoid LLM costs.
 * Use it in test/staging environments or integration tests only.
 */
@Consume.FromWorkflow(TriageWorkflow.class)
public class TriageEvaluatorConsumer extends Consumer {

    private static final Logger logger = LoggerFactory.getLogger(TriageEvaluatorConsumer.class);

    private final ComponentClient componentClient;

    public TriageEvaluatorConsumer(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    /**
     * Called whenever TriageWorkflow state changes.
     * Triggers evaluations when workflow completes successfully.
     */
    public Effect onStateChanged(TriageState state) {
        if (state == null) {
            logger.debug("Received null state, skipping evaluation");
            return effects().done();
        }

        logger.debug("🔄 Evaluator Consumer triggered - Status: {}, Workflow: {}",
                    state.status(), state.workflowId());

        // Only evaluate when workflow is COMPLETED
        if (state.status() != TriageState.Status.COMPLETED) {
            logger.debug("Workflow not completed yet, skipping evaluation");
            return effects().done();
        }

        logger.info("✨ Workflow COMPLETED - Running async evaluations for workflow: {}", state.workflowId());

        // Use the same session ID as the workflow for agent calls
        String sessionId = state.workflowId();

        // === TOXICITY EVALUATIONS ===

        // 1. Evaluate summary for toxicity
        if (state.summaryText() != null && !state.summaryText().isBlank()) {
            logger.info("🛡️  Evaluating summary for toxicity...");
            var result = componentClient
                .forAgent()
                .inSession(sessionId)
                .method(ToxicityEvaluator::evaluate)
                .invoke(state.summaryText());

            logger.info("✅ Summary toxicity evaluation complete - passed: {}", result.passed());
            var toxResult = new com.pradeepl.triage.domain.EvaluationResultsEntity.ToxicityResult(
                result.passed(),
                result.explanation()
            );
            componentClient
                .forKeyValueEntity(state.workflowId())
                .method(com.pradeepl.triage.domain.EvaluationResultsEntity::recordSummaryToxicity)
                .invoke(new com.pradeepl.triage.domain.EvaluationResultsEntity.RecordSummaryToxicity(toxResult));
        }

        // 2. Evaluate remediation for toxicity
        if (state.remediationText() != null && !state.remediationText().isBlank()) {
            logger.info("🛡️  Evaluating remediation for toxicity...");
            var result = componentClient
                .forAgent()
                .inSession(sessionId)
                .method(ToxicityEvaluator::evaluate)
                .invoke(state.remediationText());

            logger.info("✅ Remediation toxicity evaluation complete - passed: {}", result.passed());
            var toxResult = new com.pradeepl.triage.domain.EvaluationResultsEntity.ToxicityResult(
                result.passed(),
                result.explanation()
            );
            componentClient
                .forKeyValueEntity(state.workflowId())
                .method(com.pradeepl.triage.domain.EvaluationResultsEntity::recordRemediationToxicity)
                .invoke(new com.pradeepl.triage.domain.EvaluationResultsEntity.RecordRemediationToxicity(toxResult));
        }

        // === HALLUCINATION EVALUATIONS ===

        // 3. Evaluate evidence for hallucination (against incident)
        if (state.evidenceLogs() != null && !state.evidenceLogs().isBlank()) {
            logger.info("🔍 Evaluating evidence for hallucinations...");
            var request = new HallucinationEvaluator.EvaluationRequest(
                state.incident(),      // query
                state.incident(),      // reference text
                state.evidenceLogs()   // answer to evaluate
            );
            var result = componentClient
                .forAgent()
                .inSession(sessionId)
                .method(HallucinationEvaluator::evaluate)
                .invoke(request);

            logger.info("✅ Evidence hallucination evaluation complete - passed: {}", result.passed());
            var halResult = new com.pradeepl.triage.domain.EvaluationResultsEntity.HallucinationResult(
                result.passed(),
                result.explanation()
            );
            componentClient
                .forKeyValueEntity(state.workflowId())
                .method(com.pradeepl.triage.domain.EvaluationResultsEntity::recordEvidenceHallucination)
                .invoke(new com.pradeepl.triage.domain.EvaluationResultsEntity.RecordEvidenceHallucination(halResult));
        }

        // 4. Evaluate triage analysis for hallucination (against evidence + incident)
        if (state.triageText() != null && !state.triageText().isBlank()) {
            logger.info("🔍 Evaluating triage analysis for hallucinations...");
            String referenceText = buildTriageReference(state);
            var request = new HallucinationEvaluator.EvaluationRequest(
                state.incident(),      // query
                referenceText,         // reference text (incident + evidence + classification)
                state.triageText()     // answer to evaluate
            );
            var result = componentClient
                .forAgent()
                .inSession(sessionId)
                .method(HallucinationEvaluator::evaluate)
                .invoke(request);

            logger.info("✅ Triage hallucination evaluation complete - passed: {}", result.passed());
            var halResult = new com.pradeepl.triage.domain.EvaluationResultsEntity.HallucinationResult(
                result.passed(),
                result.explanation()
            );
            componentClient
                .forKeyValueEntity(state.workflowId())
                .method(com.pradeepl.triage.domain.EvaluationResultsEntity::recordTriageHallucination)
                .invoke(new com.pradeepl.triage.domain.EvaluationResultsEntity.RecordTriageHallucination(halResult));
        }

        // 5. Evaluate summary for hallucination (against all workflow outputs)
        if (state.summaryText() != null && !state.summaryText().isBlank()) {
            logger.info("🔍 Evaluating summary for hallucinations...");
            String referenceText = buildFullReference(state);
            var request = new HallucinationEvaluator.EvaluationRequest(
                state.incident(),      // query
                referenceText,         // reference text (all workflow outputs)
                state.summaryText()    // answer to evaluate
            );
            var result = componentClient
                .forAgent()
                .inSession(sessionId)
                .method(HallucinationEvaluator::evaluate)
                .invoke(request);

            logger.info("✅ Summary hallucination evaluation complete - passed: {}", result.passed());
            var halResult = new com.pradeepl.triage.domain.EvaluationResultsEntity.HallucinationResult(
                result.passed(),
                result.explanation()
            );
            componentClient
                .forKeyValueEntity(state.workflowId())
                .method(com.pradeepl.triage.domain.EvaluationResultsEntity::recordSummaryHallucination)
                .invoke(new com.pradeepl.triage.domain.EvaluationResultsEntity.RecordSummaryHallucination(halResult));
        }

        logger.info("🚀 All evaluations complete for workflow: {}", state.workflowId());

        return effects().done();
    }

    /**
     * Build reference text for triage evaluation (incident + evidence + classification).
     */
    private String buildTriageReference(TriageState state) {
        StringBuilder ref = new StringBuilder();

        ref.append("=== INCIDENT ===\n");
        ref.append(state.incident()).append("\n\n");

        if (state.classificationJson() != null && !state.classificationJson().isBlank()) {
            ref.append("=== CLASSIFICATION ===\n");
            ref.append(state.classificationJson()).append("\n\n");
        }

        if (state.evidenceLogs() != null && !state.evidenceLogs().isBlank()) {
            ref.append("=== EVIDENCE (LOGS) ===\n");
            ref.append(state.evidenceLogs()).append("\n\n");
        }

        if (state.evidenceMetrics() != null && !state.evidenceMetrics().isBlank()) {
            ref.append("=== EVIDENCE (METRICS) ===\n");
            ref.append(state.evidenceMetrics()).append("\n\n");
        }

        return ref.toString();
    }

    /**
     * Build full reference text for summary evaluation (all workflow outputs).
     */
    private String buildFullReference(TriageState state) {
        StringBuilder ref = new StringBuilder();

        ref.append(buildTriageReference(state));

        if (state.triageText() != null && !state.triageText().isBlank()) {
            ref.append("=== TRIAGE ANALYSIS ===\n");
            ref.append(state.triageText()).append("\n\n");
        }

        if (state.knowledgeBaseResult() != null && !state.knowledgeBaseResult().isBlank()) {
            ref.append("=== KNOWLEDGE BASE ===\n");
            ref.append(state.knowledgeBaseResult()).append("\n\n");
        }

        if (state.remediationText() != null && !state.remediationText().isBlank()) {
            ref.append("=== REMEDIATION ===\n");
            ref.append(state.remediationText()).append("\n\n");
        }

        return ref.toString();
    }
}

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
 * asynchronously evaluates outputs for toxicity and hallucinations.
 *
 * Uses Akka SDK's built-in evaluators:
 * - ToxicityEvaluator: Assesses hateful, inappropriate, or toxic content
 * - HallucinationEvaluator: Detects unsupported facts vs reference text
 *
 * This follows the Akka SDK pattern for async LLM evaluation:
 * - Runs evaluations after workflow completion
 * - Does not block the primary workflow
 * - Results captured for analytics and quality monitoring
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
            logger.info("🛡️  Evaluating summary for toxicity (async)...");
            // Note: Fire-and-forget - evaluation happens asynchronously
            componentClient
                .forAgent()
                .inSession(sessionId)
                .method(ToxicityEvaluator::evaluate)
                .invoke(state.summaryText());
        }

        // 2. Evaluate remediation for toxicity
        if (state.remediationText() != null && !state.remediationText().isBlank()) {
            logger.info("🛡️  Evaluating remediation for toxicity (async)...");
            componentClient
                .forAgent()
                .inSession(sessionId)
                .method(ToxicityEvaluator::evaluate)
                .invoke(state.remediationText());
        }

        // === HALLUCINATION EVALUATIONS ===

        // 3. Evaluate evidence for hallucination (against incident)
        if (state.evidenceLogs() != null && !state.evidenceLogs().isBlank()) {
            logger.info("🔍 Evaluating evidence for hallucinations (async)...");
            var request = new HallucinationEvaluator.EvaluationRequest(
                state.incident(),      // query
                state.incident(),      // reference text
                state.evidenceLogs()   // answer to evaluate
            );
            componentClient
                .forAgent()
                .inSession(sessionId)
                .method(HallucinationEvaluator::evaluate)
                .invoke(request);
        }

        // 4. Evaluate triage analysis for hallucination (against evidence + incident)
        if (state.triageText() != null && !state.triageText().isBlank()) {
            logger.info("🔍 Evaluating triage analysis for hallucinations (async)...");
            String referenceText = buildTriageReference(state);
            var request = new HallucinationEvaluator.EvaluationRequest(
                state.incident(),      // query
                referenceText,         // reference text (incident + evidence + classification)
                state.triageText()     // answer to evaluate
            );
            componentClient
                .forAgent()
                .inSession(sessionId)
                .method(HallucinationEvaluator::evaluate)
                .invoke(request);
        }

        // 5. Evaluate summary for hallucination (against all workflow outputs)
        if (state.summaryText() != null && !state.summaryText().isBlank()) {
            logger.info("🔍 Evaluating summary for hallucinations (async)...");
            String referenceText = buildFullReference(state);
            var request = new HallucinationEvaluator.EvaluationRequest(
                state.incident(),      // query
                referenceText,         // reference text (all workflow outputs)
                state.summaryText()    // answer to evaluate
            );
            componentClient
                .forAgent()
                .inSession(sessionId)
                .method(HallucinationEvaluator::evaluate)
                .invoke(request);
        }

        logger.info("🚀 All async evaluations triggered for workflow: {}", state.workflowId());

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

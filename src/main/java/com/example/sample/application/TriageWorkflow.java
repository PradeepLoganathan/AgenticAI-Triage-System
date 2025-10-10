package com.example.sample.application;

import akka.javasdk.annotations.Component;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.example.sample.domain.Conversation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.example.sample.domain.TriageState;

import static java.time.Duration.ofSeconds;

@Component(id = "triage-workflow")
public class TriageWorkflow extends Workflow<TriageState> {

    private static final Logger logger = LoggerFactory.getLogger(TriageWorkflow.class);
    private final ComponentClient componentClient;

    public TriageWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record StartTriage(String incident) {}
    public record Repeat(String message, int times) {}

    public Effect<String> start(StartTriage cmd) {
        logger.info("🚀 STARTING TRIAGE WORKFLOW - Incident: {}", cmd.incident().substring(0, Math.min(100, cmd.incident().length())) + "...");
        
        // Establish a stable agent session id for the entire workflow to demonstrate
        // bounded in-session memory behavior across multiple agent calls.
        String sessionId = UUID.randomUUID().toString();

        var init = TriageState.empty()
                .toBuilder()
                .workflowId(sessionId)
                .build()
                .addConversation(new Conversation("system", "Service triage session started"))
                .addConversation(new Conversation("user", cmd.incident()))
                .withIncident(cmd.incident())
                .withStatus(TriageState.Status.PREPARED);

        logger.debug("Initial state created, transitioning to classify step");
        return effects()
                .updateState(init)
                .transitionTo((w, i) -> step("classify"))
                .thenReply("started");
    }

    public ReadOnlyEffect<List<Conversation>> getConversations() {
        var ctx = currentState() == null ? List.<Conversation>of() : currentState().context();
        return effects().reply(ctx.size() > 1 ? ctx.subList(1, ctx.size()) : ctx);
    }

    // Utility command to grow workflow context with demo notes to illustrate
    // memory/state behavior in the UI. Adds up to 50 repeated entries.
    public Effect<String> repeat(Repeat cmd) {
        int n = cmd.times() <= 0 ? 1 : Math.min(50, cmd.times());
        String msg = (cmd.message() == null || cmd.message().isBlank()) ? "demo note" : cmd.message();

        var s = currentState();
        if (s == null) {
            return effects().reply("no-state");
        }

        var updated = s;
        for (int i = 1; i <= n; i++) {
            String entry = String.format("[DEMO] %s (%d/%d)", msg, i, n);
            updated = updated.addConversation(new Conversation("system", entry));
        }

        return effects().updateState(updated).pause().thenReply("ok");
    }


    public record StateView(
            String status,
            String incident,
            String classificationJson,
            String evidenceLogs,
            String evidenceMetrics,
            String triageText,
            String remediationText,
            String summaryText,
            String knowledgeBaseResult,
            // Memory/state visibility additions
            String agentSessionId,
            int contextEntries,
            long approxStateChars,
            long heapUsedBytes,
            long heapCommittedBytes,
            long heapMaxBytes,
            String agentMemoryMode
    ) {}

    public ReadOnlyEffect<StateView> getState() {
        var s = currentState();
        if (s == null) {
            var rt = Runtime.getRuntime();
            return effects().reply(new StateView(
                    "EMPTY", null, null, null, null, null, null, null, null,
                    null, 0, 0L,
                    rt.totalMemory() - rt.freeMemory(),
                    rt.totalMemory(),
                    rt.maxMemory(),
                    "LIMITED_WINDOW"
            ));
        }

        long approxChars = 0;
        if (s.incident() != null) approxChars += s.incident().length();
        if (s.classificationJson() != null) approxChars += s.classificationJson().length();
        if (s.evidenceLogs() != null) approxChars += s.evidenceLogs().length();
        if (s.evidenceMetrics() != null) approxChars += s.evidenceMetrics().length();
        if (s.triageText() != null) approxChars += s.triageText().length();
        if (s.remediationText() != null) approxChars += s.remediationText().length();
        if (s.summaryText() != null) approxChars += s.summaryText().length();
        if (s.knowledgeBaseResult() != null) approxChars += s.knowledgeBaseResult().length();
        int ctxSize = s.context() == null ? 0 : s.context().size();
        if (s.context() != null) {
            for (var c : s.context()) {
                if (c != null && c.content() != null) approxChars += c.content().length();
                if (c != null && c.role() != null) approxChars += c.role().length();
            }
        }

        var rt = Runtime.getRuntime();
        long used = rt.totalMemory() - rt.freeMemory();
        long committed = rt.totalMemory();
        long max = rt.maxMemory();

        return effects().reply(new StateView(
                s.status().name(),
                s.incident(),
                s.classificationJson(),
                s.evidenceLogs(),
                s.evidenceMetrics(),
                s.triageText(),
                s.remediationText(),
                s.summaryText(),
                s.knowledgeBaseResult(),
                s.workflowId(),
                ctxSize,
                approxChars,
                used,
                committed,
                max,
                "LIMITED_WINDOW"
        ));
    }

    @Override
    public WorkflowDef<TriageState> definition() {
        return workflow()
                .addStep(
                        step("classify")
                                .asyncCall(StartTriage.class, cmd -> {
                                    logger.info("🎯 STEP 1/7: CLASSIFICATION - Calling ClassifierAgent with enhanced reasoning");
                                    logger.debug("ClassifierAgent input: {}", cmd.incident().substring(0, Math.min(200, cmd.incident().length())) + "...");

                                    return CompletableFuture.supplyAsync(() ->
                                            componentClient
                                                    .forAgent()
                                                    .inSession(currentState().workflowId())
                                                    .method(ClassifierAgent::classify)
                                                    .invoke(new ClassifierAgent.Request(cmd.incident())));
                                })
                                .andThen(String.class, classificationResult -> {
                                    String service = AgentUtils.extractServiceFromClassification(classificationResult);
                                    String severity = AgentUtils.extractSeverity(classificationResult);
                                    double confidence = AgentUtils.extractConfidenceScore(classificationResult, "overall");

                                    logger.info("✅ CLASSIFICATION COMPLETE - Service: {}, Severity: {}, Confidence: {}/10", service, severity, confidence);
                                    logger.debug("ClassifierAgent output: {}", classificationResult.substring(0, Math.min(300, classificationResult.length())) + "...");

                                    String conversationEntry = String.format(
                                            "[%s] Classification completed - Service: %s, Severity: %s, Confidence: %.1f",
                                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME),
                                            service, severity, confidence);

                                    return effects()
                                            .updateState(currentState()
                                                    .withClassificationJson(classificationResult)
                                                    .addConversation(new Conversation("assistant", conversationEntry))
                                                    .withStatus(TriageState.Status.CLASSIFIED))
                                            .transitionTo(w -> step("gather_evidence"));
                                })
                )
                .addStep(
                        step("gather_evidence")
                                .asyncCall(() -> CompletableFuture.supplyAsync(() -> {
                                    String service = AgentUtils.extractServiceFromClassification(currentState().classificationJson());
                                    String severity = AgentUtils.extractSeverity(currentState().classificationJson());

                                    String metricsExpr = severity.equals("P1") ? "errors:rate1m" : "errors:rate5m";
                                    String timeRange = severity.equals("P1") ? "30m" : "1h";

                                    logger.info("🔍 STEP 2/7: EVIDENCE GATHERING - Calling EvidenceAgent for service: {} ({})", service, severity);
                                    logger.debug("EvidenceAgent params: metrics={}, timeRange={}", metricsExpr, timeRange);

                                    return componentClient
                                            .forAgent()
                                            .inSession(currentState().workflowId())
                                            .method(EvidenceAgent::gather)
                                            .invoke(new EvidenceAgent.Request(service, metricsExpr, timeRange));
                                }))
                                .andThen(String.class, evidenceResult -> {
                                    List<String> keyFindings = AgentUtils.extractKeyFindings(evidenceResult);
                                    double dataQuality = AgentUtils.extractConfidenceScore(evidenceResult, "data_quality");

                                    String[] parts = AgentUtils.extractLogsAndMetrics(evidenceResult);
                                    String logs = parts[0];
                                    String metrics = parts[1];
                                    if (logs == null && metrics == null) {
                                        logs = evidenceResult;
                                    }

                                    String conversationEntry = String.format(
                                            "[%s] Evidence analysis completed - %d key findings identified, Data quality: %.1f",
                                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME),
                                            keyFindings.size(), dataQuality);

                                    boolean needsEscalation = AgentUtils.requiresImmediateEscalation(
                                            currentState().classificationJson(), evidenceResult);
                                    if (needsEscalation) {
                                        conversationEntry += " - ESCALATION RECOMMENDED";
                                    }

                                    return effects()
                                            .updateState(currentState()
                                                    .withEvidence(logs, metrics)
                                                    .addConversation(new Conversation("assistant", conversationEntry))
                                                    .withStatus(TriageState.Status.EVIDENCE_COLLECTED))
                                            .transitionTo(w -> step("triage"));
                                })
                )
                .addStep(
                        step("triage")
                                .asyncCall(() -> CompletableFuture.supplyAsync(() -> {
                                    logger.info("🔬 STEP 3/7: TRIAGE ANALYSIS - Calling TriageAgent for systematic diagnosis");

                                    String enrichedContext = String.format(
                                            "INCIDENT CONTEXT FOR TRIAGE\n" +
                                                    "===========================\n" +
                                                    "Original Incident: %s\n\n" +
                                                    "Classification Results: %s\n\n" +
                                                    "Evidence Analysis: %s\n\n" +
                                                    "Timestamp: %s",
                                            currentState().incident(),
                                            currentState().classificationJson(),
                                            currentState().evidenceLogs() != null ? currentState().evidenceLogs() : "No evidence collected",
                                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

                                    logger.debug("TriageAgent enriched context length: {} characters", enrichedContext.length());

                                    return componentClient
                                            .forAgent()
                                            .inSession(currentState().workflowId())
                                            .method(TriageAgent::triage)
                                            .invoke(new TriageAgent.Request(enrichedContext));
                                }))
                                .andThen(String.class, triageResult -> {
                                    logger.info("✅ TriageAgent OpenAI call returned successfully");
                                    double confidence = AgentUtils.extractConfidenceScore(triageResult, "confidence");
                                    logger.info("✅ TRIAGE ANALYSIS COMPLETE - Confidence: {}/10", confidence);
                                    logger.debug("TriageAgent output length: {} chars, preview: {}",
                                            triageResult != null ? triageResult.length() : 0,
                                            triageResult != null ? triageResult.substring(0, Math.min(200, triageResult.length())) + "..." : "null");

                                    String conversationEntry = String.format(
                                            "[%s] Triage analysis completed - Analysis confidence: %.1f",
                                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME),
                                            confidence);

                                    return effects()
                                            .updateState(currentState()
                                                    .withTriageText(triageResult)
                                                    .addConversation(new Conversation("assistant", conversationEntry))
                                                    .withStatus(TriageState.Status.TRIAGED))
                                            .transitionTo(w -> step("query_knowledge_base"));
                                })
                )
                .addStep(
                        step("query_knowledge_base")
                                .asyncCall(() -> CompletableFuture.supplyAsync(() -> {
                                    logger.info("🧠 STEP 4/7: KNOWLEDGE BASE SEARCH - Calling KnowledgeBaseAgent");
                                    String service = AgentUtils.extractServiceFromClassification(currentState().classificationJson());
                                    return componentClient
                                            .forAgent()
                                            .inSession(currentState().workflowId())
                                            .method(KnowledgeBaseAgent::search)
                                            .invoke(service);
                                }))
                                .andThen(String.class, knowledgeBaseResult -> {
                                    logger.info("✅ KNOWLEDGE BASE SEARCH COMPLETE");
                                    return effects()
                                            .updateState(currentState()
                                                    .withKnowledgeBaseResult(knowledgeBaseResult)
                                                    .addConversation(new Conversation("assistant", "Knowledge base search completed."))
                                                    .withStatus(TriageState.Status.KNOWLEDGE_BASE_SEARCHED))
                                            .transitionTo(w -> step("remediate"));
                                })
                )
                .addStep(
                        step("remediate")
                                .asyncCall(() -> CompletableFuture.supplyAsync(() -> {
                                    logger.info("🛠️ STEP 5/7: REMEDIATION PLANNING - Calling RemediationAgent with risk assessment");

                                    String evidenceJson = toEvidenceJson(currentState());
                                    logger.debug("RemediationAgent inputs - Incident: {}, Evidence: {}",
                                            currentState().incident() != null ? currentState().incident().substring(0, Math.min(100, currentState().incident().length())) + "..." : "null",
                                            evidenceJson.length() > 2 ? "provided" : "empty");

                                    return componentClient
                                            .forAgent()
                                            .inSession(currentState().workflowId())
                                            .method(RemediationAgent::remediate)
                                            .invoke(new RemediationAgent.Request(
                                                    currentState().incident(),
                                                    currentState().classificationJson(),
                                                    evidenceJson,
                                                    currentState().triageText(),
                                                    currentState().knowledgeBaseResult()));
                                }))
                                .andThen(String.class, remediationResult -> {
                                    boolean isHighRisk = remediationResult != null && remediationResult.toLowerCase().contains("high");
                                    logger.info("✅ REMEDIATION PLANNING COMPLETE - Risk Level: {}", isHighRisk ? "HIGH" : "STANDARD");
                                    logger.debug("RemediationAgent output: {}", remediationResult != null ? remediationResult.substring(0, Math.min(300, remediationResult.length())) + "..." : "null");

                                    String conversationEntry = String.format(
                                            "[%s] Remediation plan completed - Ready for execution",
                                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME));
                                    if (isHighRisk) {
                                        conversationEntry += " - HIGH RISK ACTIONS IDENTIFIED";
                                    }

                                    return effects()
                                            .updateState(currentState()
                                                    .withRemediationText(remediationResult)
                                                    .addConversation(new Conversation("assistant", conversationEntry))
                                                    .withStatus(TriageState.Status.REMEDIATION_PROPOSED))
                                            .transitionTo(w -> step("summarize"));
                                })
                )
                .addStep(
                        step("summarize")
                                .asyncCall(() -> CompletableFuture.supplyAsync(() -> {
                                    logger.info("📊 STEP 6/7: SUMMARY GENERATION - Calling SummaryAgent for multi-audience communication");
                                    logger.debug("SummaryAgent processing complete workflow results for stakeholder communication");
                                    return componentClient
                                            .forAgent()
                                            .inSession(currentState().workflowId())
                                            .method(SummaryAgent::summarize)
                                            .invoke(new SummaryAgent.Request(
                                                    currentState().incident(),
                                                    currentState().classificationJson(),
                                                    currentState().triageText(),
                                                    currentState().remediationText()));
                                }))
                                .andThen(String.class, summaryResult -> {
                                    logger.info("✅ SUMMARY GENERATION COMPLETE - Multi-audience summaries ready");
                                    logger.debug("SummaryAgent output: {}", summaryResult != null ? summaryResult.substring(0, Math.min(300, summaryResult.length())) + "..." : "null");
                                    String conversationEntry = String.format(
                                            "[%s] Multi-audience summaries completed - Ready for stakeholder communication",
                                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME));
                                    return effects()
                                            .updateState(currentState()
                                                    .withSummaryText(summaryResult)
                                                    .addConversation(new Conversation("assistant", conversationEntry))
                                                    .withStatus(TriageState.Status.SUMMARY_READY))
                                            .transitionTo(w -> step("finalize"));
                                })
                )
                .addStep(
                        step("finalize")
                                .asyncCall(() -> {
                                    logger.info("🎯 STEP 7/7: FINALIZATION - Completing triage workflow");
                                    return CompletableFuture.completedStage("ok");
                                })
                                .andThen(String.class, __ -> {
                                    String service = AgentUtils.extractServiceFromClassification(currentState().classificationJson());
                                    String severity = AgentUtils.extractSeverity(currentState().classificationJson());
                                    logger.info("🏁 TRIAGE WORKFLOW COMPLETED SUCCESSFULLY");
                                    logger.info("📋 FINAL STATUS - Service: {}, Severity: {}, Status: READY FOR ACTION", service, severity);
                                    String finalEntry = String.format(
                                            "[%s] Incident triage workflow completed successfully. Service: %s, Severity: %s, Status: READY FOR ACTION",
                                            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME),
                                            service, severity);
                                    return effects()
                                            .updateState(currentState()
                                                    .withStatus(TriageState.Status.COMPLETED)
                                                    .addConversation(new Conversation("system", finalEntry)))
                                            .end();
                                })
                )
                .defaultStepTimeout(ofSeconds(300));
    }

    // Note: Reset and memory ping helpers were removed here to keep API within
    // the supported Workflow effect methods. Memory/session visibility is
    // exposed via getState(); agent session reuse happens in all steps.

    // Deprecated: Use AgentUtils.extractServiceFromClassification instead
    private static String extractService(String classificationJson) {
        return AgentUtils.extractServiceFromClassification(classificationJson);
    }

    private static String toEvidenceJson(TriageState s) {
        String logs = s.evidenceLogs();
        String metrics = s.evidenceMetrics();
        if (logs == null && metrics == null) return "{}";
        String lj = logs == null ? "null" : '"' + logs.replace("\"", "\\\"") + '"';
        String mj = metrics == null ? "null" : '"' + metrics.replace("\"", "\\\"") + '"';
        return "{" + "\"logs\":" + lj + ",\"metrics\":" + mj + "}";
    }
}

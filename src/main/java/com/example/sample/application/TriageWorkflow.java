package com.example.sample.application;

import akka.javasdk.annotations.ComponentId;
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


@ComponentId("triage-workflow")
public class TriageWorkflow extends Workflow<TriageState> {

    private static final Logger logger = LoggerFactory.getLogger(TriageWorkflow.class);
    private final ComponentClient componentClient;

    public TriageWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record StartTriage(String incident) {}

    public Effect<String> start(StartTriage cmd) {
        logger.info("🚀 STARTING TRIAGE WORKFLOW - Incident: {}", cmd.incident().substring(0, Math.min(100, cmd.incident().length())) + "...");
        
        var init = TriageState.empty()
                .addConversation(new Conversation("system", "Service triage session started"))
                .addConversation(new Conversation("user", cmd.incident()))
                .withIncident(cmd.incident())
                .withStatus(TriageState.Status.PREPARED);

        logger.debug("Initial state created, transitioning to classify step");
        return effects()
                .updateState(init)
                .transitionTo("classify", cmd)
                .thenReply("started");
    }

    public ReadOnlyEffect<List<Conversation>> getConversations() {
        var ctx = currentState() == null ? List.<Conversation>of() : currentState().context();
        return effects().reply(ctx.size() > 1 ? ctx.subList(1, ctx.size()) : ctx);
    }

    public record StateView(
            String status,
            String incident,
            String classificationJson,
            String evidenceLogs,
            String evidenceMetrics,
            String triageText,
            String remediationText,
            String summaryText
    ) {}

    public ReadOnlyEffect<StateView> getState() {
        var s = currentState();
        if (s == null) return effects().reply(new StateView("EMPTY", null, null, null, null, null, null, null));
        return effects().reply(new StateView(
                s.status().name(),
                s.incident(),
                s.classificationJson(),
                s.evidenceLogs(),
                s.evidenceMetrics(),
                s.triageText(),
                s.remediationText(),
                s.summaryText()
        ));
    }

    @Override
    public WorkflowDef<TriageState> definition() {
        return workflow()
                .addStep(classify())
                .addStep(gatherEvidence())
                .addStep(triage())
                .addStep(remediate())
                .addStep(summarize())
                .addStep(finalizeStep());
    }

    private Step classify() {
        return step("classify")
                .asyncCall(StartTriage.class, cmd -> {
                    logger.info("🎯 STEP 1/6: CLASSIFICATION - Calling ClassifierAgent with enhanced reasoning");
                    logger.debug("ClassifierAgent input: {}", cmd.incident().substring(0, Math.min(200, cmd.incident().length())) + "...");
                    
                    return CompletableFuture.supplyAsync(() ->
                            componentClient
                                    .forAgent()
                                    .inSession(UUID.randomUUID().toString())
                                    .method(ClassifierAgent::classify)
                                    .invoke(new ClassifierAgent.Request(cmd.incident())));
                })
                .andThen(String.class, classificationResult -> {
                    // Extract key information for better workflow decisions
                    String service = AgentUtils.extractServiceFromClassification(classificationResult);
                    String severity = AgentUtils.extractSeverity(classificationResult);
                    double confidence = AgentUtils.extractConfidenceScore(classificationResult, "overall");
                    
                    logger.info("✅ CLASSIFICATION COMPLETE - Service: {}, Severity: {}, Confidence: {}/10", 
                        service, severity, confidence);
                    logger.debug("ClassifierAgent output: {}", classificationResult.substring(0, Math.min(300, classificationResult.length())) + "...");
                    
                    // Enhanced conversation logging
                    String conversationEntry = String.format(
                        "[%s] Classification completed - Service: %s, Severity: %s, Confidence: %.1f",
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME),
                        service, severity, confidence
                    );
                    
                    return effects()
                            .updateState(currentState()
                                    .withClassificationJson(classificationResult)
                                    .addConversation(new Conversation("assistant", conversationEntry))
                                    .withStatus(TriageState.Status.CLASSIFIED))
                            .transitionTo("gather_evidence");
                });
    }

    private Step gatherEvidence() {
        return step("gather_evidence")
                .asyncCall(() -> CompletableFuture.supplyAsync(() -> {
                    String service = AgentUtils.extractServiceFromClassification(currentState().classificationJson());
                    String severity = AgentUtils.extractSeverity(currentState().classificationJson());
                    
                    // Adapt evidence gathering based on severity
                    String metricsExpr = severity.equals("P1") ? "errors:rate1m" : "errors:rate5m";
                    String timeRange = severity.equals("P1") ? "30m" : "1h";
                    
                    logger.info("🔍 STEP 2/6: EVIDENCE GATHERING - Calling EvidenceAgent for service: {} ({})", service, severity);
                    logger.debug("EvidenceAgent params: metrics={}, timeRange={}", metricsExpr, timeRange);
                    
                    return componentClient
                            .forAgent()
                            .inSession(UUID.randomUUID().toString())
                            .method(EvidenceAgent::gather)
                            .invoke(new EvidenceAgent.Request(service, metricsExpr, timeRange));
                }))
                .andThen(String.class, evidenceResult -> {
                    // Extract findings for better context passing
                    List<String> keyFindings = AgentUtils.extractKeyFindings(evidenceResult);
                    double dataQuality = AgentUtils.extractConfidenceScore(evidenceResult, "data_quality");
                    
                    String conversationEntry = String.format(
                        "[%s] Evidence analysis completed - %d key findings identified, Data quality: %.1f",
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME),
                        keyFindings.size(), dataQuality
                    );
                    
                    // Check if immediate escalation is needed
                    boolean needsEscalation = AgentUtils.requiresImmediateEscalation(
                        currentState().classificationJson(), evidenceResult
                    );
                    
                    if (needsEscalation) {
                        conversationEntry += " - ESCALATION RECOMMENDED";
                    }
                    
                    return effects()
                            .updateState(currentState()
                                    .withEvidence(evidenceResult, evidenceResult)
                                    .addConversation(new Conversation("assistant", conversationEntry))
                                    .withStatus(TriageState.Status.EVIDENCE_COLLECTED))
                            .transitionTo("triage");
                });
    }

    private Step triage() {
        return step("triage")
                .asyncCall(() -> CompletableFuture.supplyAsync(() -> {
                    logger.info("🔬 STEP 3/6: TRIAGE ANALYSIS - Calling TriageAgent for systematic diagnosis");
                    
                    // Build comprehensive context for triage agent
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
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    );
                    
                    logger.debug("TriageAgent enriched context length: {} characters", enrichedContext.length());
                    
                    return componentClient
                            .forAgent()
                            .inSession(UUID.randomUUID().toString())
                            .method(TriageAgent::triage)
                            .invoke(new TriageAgent.Request(enrichedContext));
                }))
                .andThen(String.class, triageResult -> {
                    logger.info("✅ TriageAgent OpenAI call returned successfully");
                    
                    // Extract key information from triage analysis
                    double confidence = AgentUtils.extractConfidenceScore(triageResult, "confidence");
                    
                    logger.info("✅ TRIAGE ANALYSIS COMPLETE - Confidence: {}/10", confidence);
                    logger.debug("TriageAgent output length: {} chars, preview: {}", 
                        triageResult != null ? triageResult.length() : 0,
                        triageResult != null ? triageResult.substring(0, Math.min(200, triageResult.length())) + "..." : "null");
                    
                    String conversationEntry = String.format(
                        "[%s] Triage analysis completed - Analysis confidence: %.1f",
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME),
                        confidence
                    );
                    
                    return effects()
                            .updateState(currentState()
                                    .withTriageText(triageResult)
                                    .addConversation(new Conversation("assistant", conversationEntry))
                                    .withStatus(TriageState.Status.TRIAGED))
                            .transitionTo("remediate");
                });
    }

    private Step remediate() {
        return step("remediate")
                .asyncCall(() -> CompletableFuture.supplyAsync(() -> {
                    logger.info("🛠️ STEP 4/6: REMEDIATION PLANNING - Calling RemediationAgent with risk assessment");
                    
                    String evidenceJson = toEvidenceJson(currentState());
                    logger.debug("RemediationAgent inputs - Incident: {}, Evidence: {}", 
                        currentState().incident() != null ? currentState().incident().substring(0, Math.min(100, currentState().incident().length())) + "..." : "null",
                        evidenceJson.length() > 2 ? "provided" : "empty");
                    
                    return componentClient
                            .forAgent()
                            .inSession(UUID.randomUUID().toString())
                            .method(RemediationAgent::remediate)
                            .invoke(new RemediationAgent.Request(
                                    currentState().incident(),
                                    currentState().classificationJson(),
                                    evidenceJson,
                                    currentState().triageText()));
                }))
                .andThen(String.class, remediationResult -> {
                    // Extract key remediation information
                    boolean isHighRisk = remediationResult != null && remediationResult.toLowerCase().contains("high");
                    
                    logger.info("✅ REMEDIATION PLANNING COMPLETE - Risk Level: {}", isHighRisk ? "HIGH" : "STANDARD");
                    logger.debug("RemediationAgent output: {}", remediationResult != null ? remediationResult.substring(0, Math.min(300, remediationResult.length())) + "..." : "null");
                    
                    String conversationEntry = String.format(
                        "[%s] Remediation plan completed - Ready for execution",
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME)
                    );
                    
                    // Check for high-risk remediation actions
                    if (isHighRisk) {
                        conversationEntry += " - HIGH RISK ACTIONS IDENTIFIED";
                    }
                    
                    return effects()
                            .updateState(currentState()
                                    .withRemediationText(remediationResult)
                                    .addConversation(new Conversation("assistant", conversationEntry))
                                    .withStatus(TriageState.Status.REMEDIATION_PROPOSED))
                            .transitionTo("summarize");
                });
    }

    private Step summarize() {
        return step("summarize")
                .asyncCall(() -> CompletableFuture.supplyAsync(() -> {
                    logger.info("📊 STEP 5/6: SUMMARY GENERATION - Calling SummaryAgent for multi-audience communication");
                    
                    logger.debug("SummaryAgent processing complete workflow results for stakeholder communication");
                    
                    return componentClient
                            .forAgent()
                            .inSession(UUID.randomUUID().toString())
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
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME)
                    );
                    
                    return effects()
                            .updateState(currentState()
                                    .withSummaryText(summaryResult)
                                    .addConversation(new Conversation("assistant", conversationEntry))
                                    .withStatus(TriageState.Status.SUMMARY_READY))
                            .transitionTo("finalize");
                });
    }

    private Step finalizeStep() {
        return step("finalize")
                .asyncCall(() -> {
                    logger.info("🎯 STEP 6/6: FINALIZATION - Completing triage workflow");
                    return CompletableFuture.completedStage("ok");
                })
                .andThen(String.class, __ -> {
                    // Calculate total processing time and add final summary
                    String service = AgentUtils.extractServiceFromClassification(currentState().classificationJson());
                    String severity = AgentUtils.extractSeverity(currentState().classificationJson());
                    
                    logger.info("🏁 TRIAGE WORKFLOW COMPLETED SUCCESSFULLY");
                    logger.info("📋 FINAL STATUS - Service: {}, Severity: {}, Status: READY FOR ACTION", service, severity);
                    
                    String finalEntry = String.format(
                        "[%s] Incident triage workflow completed successfully. " +
                        "Service: %s, Severity: %s, Status: READY FOR ACTION",
                        LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_TIME),
                        service, severity
                    );
                    
                    return effects()
                            .updateState(currentState()
                                    .withStatus(TriageState.Status.COMPLETED)
                                    .addConversation(new Conversation("system", finalEntry)))
                            .end();
                });
    }

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

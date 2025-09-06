package com.example.sample.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.example.sample.domain.Conversation;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@ComponentId("triage-workflow")
public class TriageWorkflow extends Workflow<TriageState> {

    private final ComponentClient componentClient;

    public TriageWorkflow(ComponentClient componentClient) {
        this.componentClient = componentClient;
    }

    public record StartTriage(String incident) {}

    public Effect<String> start(StartTriage cmd) {
        var init = TriageState.empty()
                .addConversation(new Conversation("system", "Service triage session started"))
                .addConversation(new Conversation("user", cmd.incident()))
                .withIncident(cmd.incident())
                .withStatus(TriageState.Status.PREPARED);

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
                .asyncCall(StartTriage.class, cmd -> CompletableFuture.supplyAsync(() ->
                        componentClient
                                .forAgent()
                                .inSession(UUID.randomUUID().toString())
                                .method(ClassifierAgent::classify)
                                .invoke(new ClassifierAgent.Request(cmd.incident()))))
                .andThen(String.class, json -> effects()
                        .updateState(currentState()
                                .withClassificationJson(json)
                                .addConversation(new Conversation("assistant", "Classification: " + json))
                                .withStatus(TriageState.Status.CLASSIFIED))
                        .transitionTo("gather_evidence")
                );
    }

    private Step gatherEvidence() {
        return step("gather_evidence")
                .asyncCall(() -> CompletableFuture.supplyAsync(() ->
                        componentClient
                                .forAgent()
                                .inSession(UUID.randomUUID().toString())
                                .method(EvidenceAgent::gather)
                                .invoke(new EvidenceAgent.Request(extractService(currentState().classificationJson()),
                                        "errors:rate5m", "1h"))
                ))
                .andThen(String.class, evidenceJson -> effects()
                        .updateState(currentState()
                                .withEvidence(evidenceJson, evidenceJson)
                                .addConversation(new Conversation("assistant", "Evidence gathered"))
                                .withStatus(TriageState.Status.EVIDENCE_COLLECTED))
                        .transitionTo("triage")
                );
    }

    private Step triage() {
        return step("triage")
                .asyncCall(() -> CompletableFuture.supplyAsync(() -> {
                    String enriched = "Incident: " + currentState().incident() +
                            "\nClassification: " + currentState().classificationJson() +
                            "\nEvidence: logs+metrics collected";
                    return componentClient
                            .forAgent()
                            .inSession(UUID.randomUUID().toString())
                            .method(TriageAgent::triage)
                            .invoke(new TriageAgent.Request(enriched));
                }))
                .andThen(String.class, triageText -> effects()
                        .updateState(currentState()
                                .withTriageText(triageText)
                                .addConversation(new Conversation("assistant", triageText))
                                .withStatus(TriageState.Status.TRIAGED))
                        .transitionTo("remediate")
                );
    }

    private Step remediate() {
        return step("remediate")
                .asyncCall(() -> CompletableFuture.supplyAsync(() ->
                        componentClient
                                .forAgent()
                                .inSession(UUID.randomUUID().toString())
                                .method(RemediationAgent::remediate)
                                .invoke(new RemediationAgent.Request(
                                        currentState().incident(),
                                        currentState().classificationJson(),
                                        toEvidenceJson(currentState()),
                                        currentState().triageText()))
                ))
                .andThen(String.class, remediation -> effects()
                        .updateState(currentState()
                                .withRemediationText(remediation)
                                .addConversation(new Conversation("assistant", remediation))
                                .withStatus(TriageState.Status.REMEDIATION_PROPOSED))
                        .transitionTo("summarize")
                );
    }

    private Step summarize() {
        return step("summarize")
                .asyncCall(() -> CompletableFuture.supplyAsync(() ->
                        componentClient
                                .forAgent()
                                .inSession(UUID.randomUUID().toString())
                                .method(SummaryAgent::summarize)
                                .invoke(new SummaryAgent.Request(
                                        currentState().incident(),
                                        currentState().classificationJson(),
                                        currentState().triageText(),
                                        currentState().remediationText()))
                ))
                .andThen(String.class, summary -> effects()
                        .updateState(currentState()
                                .withSummaryText(summary)
                                .addConversation(new Conversation("assistant", summary))
                                .withStatus(TriageState.Status.SUMMARY_READY))
                        .transitionTo("finalize")
                );
    }

    private Step finalizeStep() {
        return step("finalize")
                .asyncCall(() -> CompletableFuture.completedStage("ok"))
                .andThen(String.class, __ -> effects()
                        .updateState(currentState().withStatus(TriageState.Status.COMPLETED))
                        .end());
    }

    private static String extractService(String classificationJson) {
        if (classificationJson == null) return null;
        int i = classificationJson.indexOf("\"service\"");
        if (i < 0) return null;
        int colon = classificationJson.indexOf(':', i);
        if (colon < 0) return null;
        int q1 = classificationJson.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = classificationJson.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return classificationJson.substring(q1 + 1, q2);
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

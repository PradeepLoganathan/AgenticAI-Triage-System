package com.example.sample;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.sample.application.ClassifierAgent;
import com.example.sample.application.TriageWorkflow;
import com.example.sample.application.RemediationAgent;
import com.example.sample.application.SummaryAgent;
import com.example.sample.application.TriageAgent;
import com.example.sample.application.EvidenceAgent;
import com.example.sample.application.KnowledgeBaseAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowTest extends TestKitSupport {

    private final TestModelProvider classifierModel = new TestModelProvider();
    private final TestModelProvider triageModel = new TestModelProvider();
    private final TestModelProvider remediationModel = new TestModelProvider();
    private final TestModelProvider summaryModel = new TestModelProvider();
    private final TestModelProvider evidenceModel = new TestModelProvider();
    private final TestModelProvider knowledgeModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
                .withModelProvider(ClassifierAgent.class, classifierModel)
                .withModelProvider(TriageAgent.class, triageModel)
                .withModelProvider(RemediationAgent.class, remediationModel)
                .withModelProvider(SummaryAgent.class, summaryModel)
                .withModelProvider(EvidenceAgent.class, evidenceModel)
                .withModelProvider(KnowledgeBaseAgent.class, knowledgeModel);
    }

    @BeforeEach
    public void setup() {
        // Direct EvidenceAgent MCP calls to mock endpoint provided by this service
        System.setProperty("MCP_HTTP_URL", "http://localhost:9100/mcp");
    }

    @Test
    public void triage_workflow_completes_and_populates_state() {
        // Mock deterministic model outputs per agent
        classifierModel.fixedResponse("{\"service\":\"checkout\",\"severity\":\"P2\",\"domain\":\"payments\",\"rationale\":\"spike\"}");
        evidenceModel.fixedResponse("{\"logs\":\"mocked tool output\",\"metrics\":\"mocked tool output\"}");
        triageModel.fixedResponse("Hypothesis: db pool exhaustion\nActions: 1) rollback 2) scale db");
        remediationModel.fixedResponse("Plan: 1) rollback 2) validate 3) communicate");
        summaryModel.fixedResponse("Incident: Checkout errors\nImpact: elevated 5xx\nNext update: 30m");
        knowledgeModel.fixedResponse("Runbooks found: checkout.md, payments_incident_2023-09.md");

        var wfId = "wf-test-1";
        var incident = "Checkout 5xx spike after deploy";

        componentClient
                .forWorkflow(wfId)
                .method(TriageWorkflow::start)
                .invoke(new TriageWorkflow.StartTriage(incident));

        // Poll workflow state until completed (or timeout)
        TriageWorkflow.StateView state = null;
        int convoSize = -1;
        for (int i = 0; i < 60; i++) { // up to ~6s
            state = componentClient
                    .forWorkflow(wfId)
                    .method(TriageWorkflow::getState)
                    .invoke();
            var conv = componentClient
                    .forWorkflow(wfId)
                    .method(TriageWorkflow::getConversations)
                    .invoke();
            convoSize = conv == null ? -1 : conv.size();
            if (state != null && "COMPLETED".equals(state.status())) break;
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        }

        assertThat(state).isNotNull();
        assertThat(state.status()).isEqualTo("COMPLETED");
        assertThat(state.incident()).contains("Checkout");
        assertThat(state.classificationJson()).contains("checkout");
        assertThat(state.triageText().toLowerCase()).contains("hypothesis");
        assertThat(state.remediationText().toLowerCase()).contains("plan");
        assertThat(state.summaryText().toLowerCase()).contains("incident");
        // Conversations should include at least classification, evidence note, triage, remediation, summary
        assertThat(convoSize).isGreaterThanOrEqualTo(5);
    }
}

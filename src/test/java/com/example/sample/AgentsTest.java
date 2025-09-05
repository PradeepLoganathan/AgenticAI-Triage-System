package com.example.sample;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.sample.application.ClassifierAgent;
import com.example.sample.application.EvidenceAgent;
import com.example.sample.application.RemediationAgent;
import com.example.sample.application.SummaryAgent;
import com.example.sample.application.TriageAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AgentsTest extends TestKitSupport {

    private final TestModelProvider classifierModel = new TestModelProvider();
    private final TestModelProvider triageModel = new TestModelProvider();
    private final TestModelProvider remediationModel = new TestModelProvider();
    private final TestModelProvider summaryModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
                .withModelProvider(ClassifierAgent.class, classifierModel)
                .withModelProvider(TriageAgent.class, triageModel)
                .withModelProvider(RemediationAgent.class, remediationModel)
                .withModelProvider(SummaryAgent.class, summaryModel);
    }

    @BeforeEach
    public void setup() {
        // Route MCP calls for EvidenceAgent to the in-process mock endpoint.
        System.setProperty("MCP_HTTP_URL", "http://localhost:9100/mcp");
    }

    @Test
    public void classifier_returnsJson() {
        String json = "{\"service\":\"checkout\",\"severity\":\"P2\",\"domain\":\"payments\",\"rationale\":\"spike after deploy\"}";
        classifierModel.fixedResponse(json);

        String res = componentClient
                .forAgent()
                .method(ClassifierAgent::classify)
                .invoke(new ClassifierAgent.Request("Checkout 5xx spike after deploy"));

        assertThat(res).contains("\"service\"");
        assertThat(res).contains("checkout");
    }

    @Test
    public void evidence_gathers_from_mcp_mock() throws Exception {
        String res = componentClient
                .forAgent()
                .method(EvidenceAgent::gather)
                .invoke(new EvidenceAgent.Request("checkout", "errors:rate5m", "1h"));

        assertThat(res).contains("\"service\"");
        // Parse JSON and ensure both logs and metrics present
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var node = mapper.readTree(res);
        assertThat(node.has("logs")).isTrue();
        assertThat(node.has("metrics")).isTrue();
        assertThat(node.get("logs").asText()).contains("mocked tool output");
        assertThat(node.get("metrics").asText()).contains("mocked tool output");
    }

    @Test
    public void triage_returns_text() {
        triageModel.fixedResponse("Hypothesis: foo\nActions: 1) a 2) b");

        String res = componentClient
                .forAgent()
                .method(TriageAgent::triage)
                .invoke(new TriageAgent.Request("Some enriched context"));

        assertThat(res.toLowerCase()).contains("hypothesis");
        assertThat(res.toLowerCase()).contains("actions");
    }

    @Test
    public void remediation_returns_plan() {
        remediationModel.fixedResponse("Plan: 1) rollback 2) validate 3) communicate");
        String res = componentClient
                .forAgent()
                .method(RemediationAgent::remediate)
                .invoke(new RemediationAgent.Request("inc", "{}", "{}", "triage"));
        assertThat(res.toLowerCase()).contains("plan");
    }

    @Test
    public void summary_returns_operator_message() {
        summaryModel.fixedResponse("Incident: short\nImpact: minor\nNext update: 30m");
        String res = componentClient
                .forAgent()
                .method(SummaryAgent::summarize)
                .invoke(new SummaryAgent.Request("inc", "{}", "triage", "remediation"));
        assertThat(res.toLowerCase()).contains("incident");
        assertThat(res.toLowerCase()).contains("impact");
    }
}

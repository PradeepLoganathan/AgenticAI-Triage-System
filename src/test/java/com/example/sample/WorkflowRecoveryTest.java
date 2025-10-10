package com.example.sample;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.sample.application.ClassifierAgent;
import com.example.sample.application.EvidenceAgent;
import com.example.sample.application.TriageAgent;
import com.example.sample.application.RemediationAgent;
import com.example.sample.application.SummaryAgent;
import com.example.sample.application.TriageWorkflow;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WorkflowRecoveryTest extends TestKitSupport {

    private final TestModelProvider classifierModel = new TestModelProvider();
    private final TestModelProvider evidenceModel = new TestModelProvider();
    private final TestModelProvider triageModel = new TestModelProvider();
    private final TestModelProvider remediationModel = new TestModelProvider();
    private final TestModelProvider summaryModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
                .withModelProvider(ClassifierAgent.class, classifierModel)
                .withModelProvider(EvidenceAgent.class, evidenceModel)
                .withModelProvider(TriageAgent.class, triageModel)
                .withModelProvider(RemediationAgent.class, remediationModel)
                .withModelProvider(SummaryAgent.class, summaryModel);
    }

    @Test
    public void workflow_fails_over_to_interrupt_step() {
        var wfId = "wf-recovery-test";

        // Provide minimal deterministic responses to avoid network calls
        classifierModel.fixedResponse("{\"service\":\"checkout\",\"severity\":\"P2\"}");
        evidenceModel.fixedResponse("{\"logs\":\"ok\",\"metrics\":\"ok\"}");
        triageModel.fixedResponse("ok");
        remediationModel.fixedResponse("ok");
        summaryModel.fixedResponse("ok");

        // Start to initialize state (this will begin classification immediately)
        componentClient
                .forWorkflow(wfId)
                .method(TriageWorkflow::start)
                .invoke(new TriageWorkflow.StartTriage("incident to init"));

        // Force a failure to trigger defaultStepRecovery -> interrupt
        var res = componentClient
                .forWorkflow(wfId)
                .method(TriageWorkflow::forceFail)
                .invoke();
        assertThat(res).isEqualTo("triggered");

        // Poll conversations and state; expect interrupt note and workflow ended
        var interrupted = false;
        for (int i = 0; i < 50; i++) {
            var conv = componentClient
                    .forWorkflow(wfId)
                    .method(TriageWorkflow::getConversations)
                    .invoke();
            if (conv != null) {
                interrupted = conv.stream().anyMatch(c -> c.content().contains("Workflow interrupted"));
            }
            if (interrupted) break;
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
        }

        assertThat(interrupted).isTrue();
    }
}

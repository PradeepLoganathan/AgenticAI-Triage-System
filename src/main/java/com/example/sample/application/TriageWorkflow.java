package com.example.sample.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.workflow.Workflow;
import com.example.sample.domain.Conversation;

import java.util.List;
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
            .withStatus(TriageState.Status.PREPARED);

        return effects()
            .updateState(init)
            .transitionTo("agent_call", cmd)
            .thenReply("started");
    }

    public ReadOnlyEffect<List<Conversation>> getConversations() {
        var ctx = currentState() == null ? List.<Conversation>of() : currentState().context();
        return effects().reply(ctx.size() > 1 ? ctx.subList(1, ctx.size()) : ctx);
    }

    @Override
    public WorkflowDef<TriageState> definition() {
        return workflow()
            .addStep(agentCall())
            .addStep(finalizeStep());
    }

    private Step agentCall() {
        return step("agent_call")
            .asyncCall(StartTriage.class, cmd ->
                CompletableFuture.supplyAsync(() ->
                    componentClient
                        .forAgent()
                        .method(TriageAgent::triage)
                        .invoke(new TriageAgent.Request(cmd.incident()))
                )
            )
            .andThen(String.class, response ->
                effects()
                    .updateState(
                        currentState()
                            .addConversation(new Conversation("assistant", response))
                            .withStatus(TriageState.Status.THINKING)
                    )
                    .transitionTo("finalize")
            );
    }

    private Step finalizeStep() {
        return step("finalize")
            .asyncCall(() -> CompletableFuture.completedStage("ok"))
            .andThen(String.class, __ -> effects()
                .updateState(currentState().withStatus(TriageState.Status.COMPLETED))
                .end()
            );
    }
}


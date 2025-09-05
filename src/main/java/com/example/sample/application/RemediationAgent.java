package com.example.sample.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.ComponentId;

@ComponentId("remediation-agent")
public class RemediationAgent extends Agent {

    private static final String SYSTEM = """
        You are a production remediation assistant. Given classification, evidence (logs/metrics), and a triage write-up,
        propose a safe, staged remediation plan.
        Return a concise plan:
        - Risks: <short list>
        - Plan: <3-7 numbered steps> (include validation/rollback)
        - Owners: <who should act>
        Keep it actionable and ops-friendly.
        """;

    public record Request(String incident, String classificationJson, String evidenceJson, String triageText) {}

    public Effect<String> remediate(Request req) {
        String prompt = "Incident:\n" + req.incident() +
                "\n\nClassification JSON:\n" + req.classificationJson() +
                "\n\nEvidence JSON:\n" + req.evidenceJson() +
                "\n\nTriage:\n" + req.triageText();

        return effects()
                .model(
                        ModelProvider.openAi()
                                .withApiKey(System.getenv("OPENAI_API_KEY"))
                                .withModelName("gpt-4o-mini")
                                .withTemperature(0.2)
                                .withMaxTokens(800)
                )
                .memory(MemoryProvider.none())
                .systemMessage(SYSTEM)
                .userMessage(prompt)
                .thenReply();
    }
}


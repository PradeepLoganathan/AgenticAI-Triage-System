package com.example.sample.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.ComponentId;

@ComponentId("summary-agent")
public class SummaryAgent extends Agent {

    private static final String SYSTEM = """
        You are an incident communications assistant. Produce a short operator-facing summary with:
        - Incident: one-liner
        - Impact: bullet points
        - Root cause (suspected)
        - Actions taken / planned
        - Next update ETA
        Keep under 12 lines.
        """;

    public record Request(String incident, String classificationJson, String triageText, String remediationText) {}

    public Effect<String> summarize(Request req) {
        String prompt = "Incident One-liner:\n" + req.incident() +
                "\n\nClassification:\n" + req.classificationJson() +
                "\n\nTriage:\n" + req.triageText() +
                "\n\nRemediation:\n" + req.remediationText();

        return effects()
                .model(
                        ModelProvider.openAi()
                                .withApiKey(System.getenv("OPENAI_API_KEY"))
                                .withModelName("gpt-4o-mini")
                                .withTemperature(0.2)
                                .withMaxTokens(600)
                )
                .memory(MemoryProvider.none())
                .systemMessage(SYSTEM)
                .userMessage(prompt)
                .thenReply();
    }
}


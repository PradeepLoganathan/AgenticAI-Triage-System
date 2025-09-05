package com.example.sample.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.ComponentId;

@ComponentId("triage-agent")
public class TriageAgent extends Agent {

    private static final String SYSTEM = """
        You are an on-call service triage assistant. Given an incident summary,
        produce a concise root-cause hypothesis and next best actions.
        Return plain text with two sections:
        - Hypothesis: <one sentence>
        - Actions: <3-5 bullet points>
        Keep it pragmatic and production-focused.
        """;

    public record Request(String incident) {}

    public Effect<String> triage(Request request) {
        return effects()
            .model(
                ModelProvider.openAi()
                    .withApiKey(System.getenv("OPENAI_API_KEY"))
                    .withModelName("gpt-4o-mini")
                    .withTemperature(0.2)
                    .withMaxTokens(800)
            )
            .memory(MemoryProvider.limitedWindow())
            .systemMessage(SYSTEM)
            .userMessage(request.incident())
            .thenReply();
    }
}


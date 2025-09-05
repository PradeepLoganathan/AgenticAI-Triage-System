package com.example.sample.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.ComponentId;

@ComponentId("classifier-agent")
public class ClassifierAgent extends Agent {

    private static final String SYSTEM = """
        You are an incident classifier. Given a plain-text incident summary, classify:
        - service: the most likely impacted service name (one word or kebab-case)
        - severity: one of: P1, P2, P3, P4
        - domain: e.g., checkout, payments, catalog, auth
        Return strict JSON: {\"service\":\"...\",\"severity\":\"...\",\"domain\":\"...\",\"rationale\":\"...\"}
        No extra text.
        """;

    public record Request(String incident) {}

    public Effect<String> classify(Request req) {
        return effects()
                .model(
                        ModelProvider.openAi()
                                .withApiKey(System.getenv("OPENAI_API_KEY"))
                                .withModelName("gpt-4o-mini")
                                .withTemperature(0.1)
                                .withMaxTokens(400)
                )
                .memory(MemoryProvider.none())
                .systemMessage(SYSTEM)
                .userMessage(req.incident())
                .thenReply();
    }
}


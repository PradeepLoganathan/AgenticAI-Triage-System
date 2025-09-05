package com.example.sample.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.annotations.Description;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ComponentId("triage-agent")
public class TriageAgent extends Agent {

    private static final String SYSTEM = """
        You are an on-call service triage assistant. Given an incident summary,
        produce a concise root-cause hypothesis and next best actions.
        Return plain text with two sections:
        - Hypothesis: <one sentence>
        - Actions: <3-5 bullet points>
        Keep it pragmatic and production-focused.
        
        If you need logs, metrics, or external context, you may call the tool `mcp-call`
        with a tool name and JSON arguments to retrieve additional information before answering.
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
            .tools(this)
            .systemMessage(SYSTEM)
            .userMessage(request.incident())
            .thenReply();
    }

    @FunctionTool(name = "mcp-call", description = "Call a Model Context Protocol tool via an HTTP JSON-RPC bridge. Configure MCP_HTTP_URL.")
    public String mcpCall(
            @Description("Tool name to invoke on the MCP server") String toolName,
            @Description("JSON string of arguments for the tool call") String argumentsJson
    ) {
        String endpoint = System.getProperty("MCP_HTTP_URL",
                System.getenv().getOrDefault("MCP_HTTP_URL", "http://localhost:7400/jsonrpc"));
        String id = java.util.UUID.randomUUID().toString();

        // Basic escape for tool name to embed in JSON
        String safeTool = toolName == null ? "" : toolName.replace("\\", "\\\\").replace("\"", "\\\"");
        String args = (argumentsJson == null || argumentsJson.isBlank()) ? "{}" : argumentsJson;

        String payload = "{" +
                "\"jsonrpc\":\"2.0\"," +
                "\"id\":\"" + id + "\"," +
                "\"method\":\"call_tool\"," +
                "\"params\":{" +
                "\"name\":\"" + safeTool + "\"," +
                "\"arguments\":" + args +
                "}}";

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            String body = response.body();
            if (status >= 200 && status < 300) {
                return body;
            } else {
                return "MCP_ERROR: HTTP " + status + ": " + body;
            }
        } catch (Exception e) {
            return "MCP_ERROR: " + e.getMessage();
        }
    }
}

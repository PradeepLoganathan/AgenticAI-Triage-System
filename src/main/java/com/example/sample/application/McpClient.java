package com.example.sample.application;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

public class McpClient {

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String endpoint;

    public McpClient() {
        this.endpoint = System.getProperty("MCP_HTTP_URL",
                System.getenv().getOrDefault("MCP_HTTP_URL", "http://localhost:7400/jsonrpc"));
    }

    public String callTool(String toolName, String argumentsJson) {
        String id = UUID.randomUUID().toString();
        String safeTool = toolName == null ? "" : toolName.replace("\\", "\\\\").replace("\"", "\\\"");
        String args = (argumentsJson == null || argumentsJson.isBlank()) ? "{}" : argumentsJson;
        String payload = "{" +
                "\"jsonrpc\":\"2.0\"," +
                "\"id\":\"" + id + "\"," +
                "\"method\":\"call_tool\"," +
                "\"params\":{\"name\":\"" + safeTool + "\",\"arguments\":" + args + "}}";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return res.body();
        } catch (Exception e) {
            return "MCP_ERROR: " + e.getMessage();
        }
    }

    public String fetchLogs(String service, int lines) {
        String args = String.format("{\"service\":\"%s\",\"lines\":%d}",
                escape(service), lines);
        return callTool("fetch_logs", args);
    }

    public String queryMetrics(String expr, String range) {
        String args = String.format("{\"expr\":\"%s\",\"range\":\"%s\"}",
                escape(expr), escape(range));
        return callTool("query_metrics", args);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}


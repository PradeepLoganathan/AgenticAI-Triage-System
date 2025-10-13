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
        // Resolve MCP endpoint in order: System property -> Env var -> application.conf -> default
        String fromSys = System.getProperty("MCP_HTTP_URL");
        String fromEnv = System.getenv("MCP_HTTP_URL");
        String fromConf = null;
        try {
            // Avoid hard dependency if config not present
            com.typesafe.config.Config cfg = com.typesafe.config.ConfigFactory.load();
            if (cfg.hasPath("mcp.http.url")) {
                fromConf = cfg.getString("mcp.http.url");
            }
        } catch (Throwable ignored) {}
        String fallback = "http://localhost:9100/mcp"; // in-app default MCP endpoint
        this.endpoint = coalesce(fromSys, fromEnv, fromConf, fallback);
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

    private static String coalesce(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}

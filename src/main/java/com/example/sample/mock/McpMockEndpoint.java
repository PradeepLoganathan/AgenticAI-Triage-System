package com.example.sample.mock;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.HttpResponses;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/mcp")
public class McpMockEndpoint {

    private static final ObjectMapper mapper = new ObjectMapper();

    @Post
    public HttpResponse call(JsonNode req) {
        try {
            String id = req.has("id") ? req.get("id").toString() : "null";
            String method = req.has("method") ? req.get("method").asText("") : "";
            ObjectNode response = mapper.createObjectNode();
            response.put("jsonrpc", "2.0");
            if (!"call_tool".equals(method)) {
                response.set("id", req.get("id"));
                ObjectNode err = mapper.createObjectNode();
                err.put("code", -32601);
                err.put("message", "Method not found");
                response.set("error", err);
                return HttpResponses.ok(response.toString());
            }

            JsonNode params = req.path("params");
            String name = params.path("name").asText("");
            JsonNode args = params.path("arguments");
            // arguments may come as JSON string; parse if so
            if (args.isTextual()) {
                try { args = mapper.readTree(args.asText()); } catch (Exception ignored) {}
            }

            ObjectNode result;
            switch (name) {
                case "fetch_logs" -> result = handleFetchLogs(args);
                case "query_metrics" -> result = handleQueryMetrics(args);
                default -> {
                    result = mapper.createObjectNode();
                    result.put("error", "Unknown tool: " + name);
                }
            }

            response.set("id", req.get("id"));
            response.set("result", result);
            return HttpResponses.ok(response.toString());
        } catch (Exception e) {
            // Return JSON-RPC error object
            String resp = String.format("{\"jsonrpc\":\"2.0\",\"id\":null,\"error\":{\"code\":-32603,\"message\":\"Internal error: %s\"}}",
                    escape(e.getMessage()));
            return HttpResponses.ok(resp);
        }
    }

    private ObjectNode handleFetchLogs(JsonNode args) {
        String service = args.path("service").asText("");
        int lines = args.path("lines").isInt() ? args.get("lines").asInt() : 200;
        String range = args.path("range").asText(null);

        String fileName = String.format("logs/%s.log", service);
        String logs = readResourceText(fileName);
        if (logs == null) {
            logs = String.format("No log file found for service: %s", service);
        }
        String[] allLines = logs.split("\n");
        int startIdx = Math.max(0, allLines.length - lines);
        int actual = Math.min(lines, allLines.length);
        StringBuilder tail = new StringBuilder();
        for (int i = startIdx; i < allLines.length; i++) tail.append(allLines[i]).append('\n');

        ObjectNode result = mapper.createObjectNode();
        result.put("logs", tail.toString());
        result.put("source", "classpath");
        result.put("linesReturned", actual);
        if (range != null) result.put("range", range);
        result.put("service", service);
        return result;
    }

    private ObjectNode handleQueryMetrics(JsonNode args) {
        String expr = args.path("expr").asText("");
        String range = args.path("range").asText("");
        String file = determineMetricsFile(expr);
        String raw = readResourceText(file);
        if (raw == null) raw = String.format("No metrics file found for query: %s", expr);

        ObjectNode result = mapper.createObjectNode();
        result.put("raw", raw);
        result.put("source", "classpath");
        result.put("expr", expr);
        result.put("range", range);
        return result;
    }

    private static String determineMetricsFile(String expr) {
        if (expr.contains("error") || expr.contains("fail")) {
            return "metrics/payment-service-errors.json";
        } else if (expr.contains("latency") || expr.contains("response_time")) {
            return "metrics/payment-service-latency.json";
        } else if (expr.contains("cpu") || expr.contains("memory") || expr.contains("resource")) {
            return "metrics/user-service-resources.json";
        } else if (expr.contains("throughput") || expr.contains("rate")) {
            return "metrics/order-service-throughput.json";
        } else {
            return "metrics/payment-service-errors.json";
        }
    }

    private static String readResourceText(String path) {
        try {
            InputStream in = McpMockEndpoint.class.getClassLoader().getResourceAsStream(path);
            if (in == null) return null;
            byte[] bytes = in.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

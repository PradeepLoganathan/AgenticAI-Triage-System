package com.example.sample.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;

@ComponentId("evidence-agent")
public class EvidenceAgent extends Agent {

    private final McpClient mcp = new McpClient();

    public record Request(String service, String metricsExpr, String range) {}

    public Effect<String> gather(Request req) {
        String svc = req.service() == null || req.service().isBlank() ? "unknown" : req.service();
        String logs = mcp.fetchLogs(svc, 200);
        String metrics = mcp.queryMetrics(req.metricsExpr() == null ? "errors:rate5m" : req.metricsExpr(),
                req.range() == null ? "1h" : req.range());

        String json = "{" +
                "\"service\":\"" + escape(svc) + "\"," +
                "\"logs\":" + toJsonString(logs) + "," +
                "\"metrics\":" + toJsonString(metrics) +
                "}";

        // Return JSON. Keep model off by default; if you need summarization, enable below.
        return effects()
                .memory(MemoryProvider.none())
                .reply(json);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String toJsonString(String s) {
        if (s == null) return "null";
        String v = s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
        return "\"" + v + "\"";
    }
}


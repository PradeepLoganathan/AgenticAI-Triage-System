package com.example.sample.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.annotations.Description;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

@ComponentId("evidence-agent")
public class EvidenceAgent extends Agent {

    private final McpClient mcp = new McpClient();
    
    private static final String SYSTEM = """
        You are an expert evidence collection and analysis agent for incident response.
        
        Your role is to intelligently gather and analyze evidence from multiple sources.
        
        PROCESS:
        1. ANALYZE the service and incident type to determine what evidence is needed
        2. GATHER evidence strategically (logs, metrics, traces, alerts)
        3. ANALYZE the collected evidence for patterns, anomalies, and correlations
        4. SYNTHESIZE findings into actionable insights
        
        EVIDENCE SOURCES AVAILABLE:
        - fetch_logs: Get service logs for specific timeframes
        - query_metrics: Get metrics and performance data
        - correlate_evidence: Analyze relationships between different evidence
        
        ANALYSIS GUIDELINES:
        - Look for error patterns, performance anomalies, and correlations
        - Consider temporal relationships (what happened when)
        - Identify root causes vs symptoms
        - Flag critical vs informational findings
        - Recommend additional evidence if needed
        
        Return structured JSON with your analysis:
        {
          "collection_strategy": "explanation of your evidence gathering approach",
          "evidence_summary": {
            "logs": {"summary": "key findings", "error_count": N, "patterns": []},
            "metrics": {"summary": "performance insights", "anomalies": [], "trends": []}
          },
          "analysis": {
            "key_findings": ["finding1", "finding2"],
            "correlations": ["correlation1", "correlation2"],
            "anomalies": ["anomaly1", "anomaly2"],
            "timeline": ["event1 at time1", "event2 at time2"]
          },
          "confidence_assessment": {
            "data_quality": 8,
            "completeness": 7,
            "reliability": 9
          },
          "recommendations": {
            "critical_evidence": ["what strongly indicates the issue"],
            "additional_data_needed": ["what other evidence would help"],
            "next_investigation_steps": ["suggested next actions"]
          }
        }
        """;

    public record Request(String service, String metricsExpr, String range) {}
    
    public record EvidenceAnalysis(
        String service,
        String rawLogs,
        String rawMetrics,
        List<String> errorPatterns,
        List<String> anomalies,
        int errorCount
    ) {}

    public Effect<String> gather(Request req) {
        String contextualPrompt = String.format(
            "Incident Evidence Collection Request:\n" +
            "- Service: %s\n" +
            "- Metrics Expression: %s\n" +
            "- Time Range: %s\n" +
            "- Current Time: %s\n\n" +
            "Please develop a comprehensive evidence collection and analysis strategy for this incident.",
            req.service() != null ? req.service() : "unknown",
            req.metricsExpr() != null ? req.metricsExpr() : "errors:rate5m",
            req.range() != null ? req.range() : "1h",
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
        
        return effects()
                .model(
                        ModelProvider.openAi()
                                .withApiKey(System.getenv("OPENAI_API_KEY"))
                                .withModelName("gpt-4o-mini")
                                .withTemperature(0.3)
                                .withMaxTokens(2000)
                )
                .memory(MemoryProvider.limitedWindow())
                .tools(this)
                .systemMessage(SYSTEM)
                .userMessage(contextualPrompt)
                .thenReply();
    }

    @FunctionTool(name = "fetch_logs", description = "Fetch service logs for analysis")
    public String fetchLogs(
            @Description("Service name to fetch logs from") String service,
            @Description("Number of log lines to fetch") int lines
    ) {
        String logs = mcp.fetchLogs(service, lines);
        EvidenceAnalysis analysis = analyzeLogs(logs, service);
        
        return String.format(
            "Logs Analysis for %s:\n" +
            "- Total lines fetched: %d\n" +
            "- Error count: %d\n" +
            "- Error patterns found: %s\n" +
            "- Anomalies detected: %s\n\n" +
            "Raw logs: %s",
            service, lines, analysis.errorCount(),
            String.join(", ", analysis.errorPatterns()),
            String.join(", ", analysis.anomalies()),
            logs
        );
    }
    
    @FunctionTool(name = "query_metrics", description = "Query performance metrics with analysis")
    public String queryMetrics(
            @Description("Metrics expression to query") String expr,
            @Description("Time range for the query") String range
    ) {
        String metrics = mcp.queryMetrics(expr, range);
        List<String> insights = analyzeMetrics(metrics, expr);
        
        return String.format(
            "Metrics Analysis for '%s' over %s:\n" +
            "- Key insights: %s\n\n" +
            "Raw metrics: %s",
            expr, range,
            String.join(", ", insights),
            metrics
        );
    }
    
    @FunctionTool(name = "correlate_evidence", description = "Correlate findings across different evidence sources")
    public String correlateEvidence(
            @Description("Description of log findings") String logFindings,
            @Description("Description of metric findings") String metricFindings
    ) {
        return String.format(
            "Evidence Correlation Analysis:\n" +
            "Log patterns: %s\n" +
            "Metric patterns: %s\n\n" +
            "Potential correlations:\n" +
            "- Timeline alignment between error spikes and performance degradation\n" +
            "- Service dependency failures coinciding with error increases\n" +
            "- Resource exhaustion patterns matching error patterns\n\n" +
            "Confidence: High if patterns align temporally, Medium if partial alignment, Low if no clear correlation",
            logFindings, metricFindings
        );
    }
    
    private EvidenceAnalysis analyzeLogs(String logs, String service) {
        if (logs == null) {
            return new EvidenceAnalysis(service, "", "", List.of(), List.of(), 0);
        }
        
        // Pattern matching for common error types
        List<String> errorPatterns = new ArrayList<>();
        List<String> anomalies = new ArrayList<>();
        int errorCount = 0;
        
        // Common error patterns
        Pattern errorPattern = Pattern.compile("(?i)(error|exception|failed|timeout|refused)");
        Pattern httpErrorPattern = Pattern.compile("(?i)(5\\d{2}|4\\d{2})");
        Pattern dbErrorPattern = Pattern.compile("(?i)(connection.*refused|deadlock|timeout.*database)");
        
        String[] lines = logs.split("\\n");
        for (String line : lines) {
            if (errorPattern.matcher(line).find()) {
                errorCount++;
            }
            
            Matcher httpMatcher = httpErrorPattern.matcher(line);
            if (httpMatcher.find()) {
                errorPatterns.add("HTTP " + httpMatcher.group(1) + " errors");
            }
            
            if (dbErrorPattern.matcher(line).find()) {
                errorPatterns.add("Database connectivity issues");
            }
        }
        
        // Detect anomalies
        if (errorCount > lines.length * 0.1) {
            anomalies.add("High error rate (" + errorCount + " errors in " + lines.length + " lines)");
        }
        
        return new EvidenceAnalysis(service, logs, "", errorPatterns, anomalies, errorCount);
    }
    
    private List<String> analyzeMetrics(String metrics, String expr) {
        List<String> insights = new ArrayList<>();
        
        if (metrics == null || metrics.isEmpty()) {
            insights.add("No metrics data available");
            return insights;
        }
        
        // Simple heuristic analysis
        if (expr.contains("error")) {
            insights.add("Error rate metrics requested - indicates error investigation");
        }
        if (expr.contains("latency") || expr.contains("response_time")) {
            insights.add("Performance metrics requested - indicates latency investigation");
        }
        if (expr.contains("cpu") || expr.contains("memory")) {
            insights.add("Resource utilization metrics - indicates capacity investigation");
        }
        
        // Look for numeric patterns that might indicate issues
        if (metrics.contains("100%") || metrics.contains("0.00")) {
            insights.add("Extreme values detected - potential system limits or failures");
        }
        
        return insights;
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


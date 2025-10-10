package com.example.sample.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.annotations.Description;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@ComponentId("evidence-agent")
public class EvidenceAgent extends Agent {

    private static final Logger logger = LoggerFactory.getLogger(EvidenceAgent.class);
    
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
        int errorCount,
        java.util.Map<String,Integer> statusCodeCounts,
        List<String> sampleErrorLines
    ) {}

    public Effect<String> gather(Request req) {
        logger.info("🔍 EvidenceAgent.gather() STARTING - Service: {}, Metrics: {}, Range: {}", 
            req.service(), req.metricsExpr(), req.range());
        logger.debug("EvidenceAgent OpenAI API key present: {}", System.getenv("OPENAI_API_KEY") != null);
        
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
        
        logger.debug("EvidenceAgent prompt length: {} chars", contextualPrompt.length());
        long startTime = System.currentTimeMillis();
        
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
        logger.info("📝 EvidenceAgent.fetchLogs() called - Service: {}, Lines: {}", service, lines);
        String logs = readLogsFromFile(service, lines);
        EvidenceAnalysis analysis = analyzeLogs(logs, service);
        logger.debug("📝 fetchLogs analysis - Error count: {}, Patterns: {}, Status codes: {}", 
            analysis.errorCount(), analysis.errorPatterns().size(), analysis.statusCodeCounts());
        
        return String.format(
            "Logs Analysis for %s:\n" +
            "- Total lines fetched: %d\n" +
            "- Error count: %d\n" +
            "- Error patterns found: %s\n" +
            "- HTTP status counts: %s\n" +
            "- Anomalies detected: %s\n" +
            "- Sample error lines: %s\n\n" +
            "Raw logs: %s",
            service, lines, analysis.errorCount(),
            String.join(", ", analysis.errorPatterns()),
            analysis.statusCodeCounts().isEmpty() ? "{}" : analysis.statusCodeCounts().toString(),
            String.join(" | ", analysis.anomalies()),
            String.join(" \n ", analysis.sampleErrorLines()),
            logs
        );
    }
    
    private String readLogsFromFile(String service, int maxLines) {
        try {
            String fileName = String.format("logs/%s.log", service);
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
            
            if (inputStream == null) {
                return String.format("No log file found for service: %s", service);
            }
            
            String fullLogs = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            String[] allLines = fullLogs.split("\n");
            
            // Return last N lines (most recent logs)
            int startIndex = Math.max(0, allLines.length - maxLines);
            int actualLines = Math.min(maxLines, allLines.length);
            
            StringBuilder result = new StringBuilder();
            for (int i = startIndex; i < allLines.length; i++) {
                result.append(allLines[i]).append("\n");
            }
            
            return String.format("Recent %d lines from %s:\n%s", actualLines, service, result.toString());
            
        } catch (IOException e) {
            return String.format("Error reading logs for %s: %s", service, e.getMessage());
        }
    }
    
    @FunctionTool(name = "query_metrics", description = "Query performance metrics with analysis")
    public String queryMetrics(
            @Description("Metrics expression to query") String expr,
            @Description("Time range for the query") String range
    ) {
        logger.info("📊 EvidenceAgent.queryMetrics() called - Expr: {}, Range: {}", expr, range);
        String metrics = readMetricsFromFile(expr, range);
        List<String> insights = analyzeMetrics(metrics, expr);
        logger.debug("📊 queryMetrics analysis - Insights count: {}", insights.size());
        
        return String.format(
            "Metrics Analysis for '%s' over %s:\n" +
            "- Key insights: %s\n\n" +
            "Raw metrics: %s",
            expr, range,
            String.join(", ", insights),
            metrics
        );
    }
    
    private String readMetricsFromFile(String expr, String range) {
        try {
            String fileName = determineMetricsFile(expr);
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
            
            if (inputStream == null) {
                return String.format("No metrics file found for query: %s", expr);
            }
            
            ObjectMapper mapper = new ObjectMapper();
            JsonNode metricsData = mapper.readTree(inputStream);
            
            return formatMetricsOutput(metricsData, expr, range);
            
        } catch (IOException e) {
            return String.format("Error reading metrics for query '%s': %s", expr, e.getMessage());
        }
    }
    
    private String determineMetricsFile(String expr) {
        if (expr.contains("error") || expr.contains("fail")) {
            return "metrics/payment-service-errors.json";
        } else if (expr.contains("latency") || expr.contains("response_time")) {
            return "metrics/payment-service-latency.json";
        } else if (expr.contains("cpu") || expr.contains("memory") || expr.contains("resource")) {
            return "metrics/user-service-resources.json";
        } else if (expr.contains("throughput") || expr.contains("rate")) {
            return "metrics/order-service-throughput.json";
        } else {
            // Default to error metrics if unsure
            return "metrics/payment-service-errors.json";
        }
    }
    
    private String formatMetricsOutput(JsonNode metricsData, String expr, String range) {
        StringBuilder output = new StringBuilder();
        output.append("Metrics Data Summary:\n");
        
        // Extract key metrics based on the data structure
        JsonNode metrics = metricsData.get("metrics");
        if (metrics == null) {
            return "Invalid metrics file format";
        }
        
        // Format error metrics
        if (metrics.has("error_rate")) {
            JsonNode errorRate = metrics.get("error_rate");
            output.append(String.format("- Error Rate: %.1f%% (%s), Previous: %.1f%%\n", 
                errorRate.get("current").asDouble(),
                errorRate.get("status").asText(),
                errorRate.get("previous_hour").asDouble()));
                
            JsonNode errorCount = metrics.get("error_count");
            output.append(String.format("- Total Errors: %d requests\n", 
                errorCount.get("total").asInt()));
                
            if (metrics.has("error_spike") && metrics.get("error_spike").get("detected").asBoolean()) {
                JsonNode spike = metrics.get("error_spike");
                output.append(String.format("- Spike Detected: %s (peak: %.1f%%, cause: %s)\n",
                    spike.get("time_window").asText(),
                    spike.get("peak_rate").asDouble(),
                    spike.get("primary_cause").asText()));
            }
        }
        
        // Format latency metrics
        if (metrics.has("latency_percentiles")) {
            JsonNode latency = metrics.get("latency_percentiles");
            output.append(String.format("- Latency P95: %dms, P99: %dms, P99.9: %dms\n",
                latency.get("p95").asInt(),
                latency.get("p99").asInt(),
                latency.get("p99.9").asInt()));
                
            JsonNode avgLatency = metrics.get("average_latency");
            output.append(String.format("- Average Latency: %dms (%s), Baseline: %dms\n",
                avgLatency.get("current").asInt(),
                avgLatency.get("status").asText(),
                avgLatency.get("baseline").asInt()));
        }
        
        // Format resource metrics  
        if (metrics.has("cpu_utilization")) {
            JsonNode cpu = metrics.get("cpu_utilization");
            output.append(String.format("- CPU Usage: %.1f%% (%s), Peak: %.1f%%\n",
                cpu.get("current").asDouble(),
                cpu.get("status").asText(),
                cpu.get("peak_15min").asDouble()));
                
            JsonNode memory = metrics.get("memory_utilization");
            output.append(String.format("- Memory: Heap %.1f%%, GC Pressure: %s\n",
                memory.get("heap_used").asDouble(),
                memory.get("gc_pressure").asText()));
        }
        
        // Format throughput metrics
        if (metrics.has("request_rate")) {
            JsonNode requestRate = metrics.get("request_rate");
            output.append(String.format("- Request Rate: %d req/sec, Peak: %d req/sec\n",
                requestRate.get("current").asInt(),
                requestRate.get("peak_1h").asInt()));
                
            JsonNode successRate = metrics.get("success_rate");
            output.append(String.format("- Success Rate: %.1f%% (%s), Target: %.1f%%\n",
                successRate.get("current").asDouble(),
                successRate.get("status").asText(),
                successRate.get("target").asDouble()));
        }
        
        // Add alerts if present
        if (metrics.has("alerts")) {
            JsonNode alerts = metrics.get("alerts");
            if (alerts.isArray() && alerts.size() > 0) {
                output.append("- Active Alerts: ");
                for (JsonNode alert : alerts) {
                    output.append(alert.asText()).append("; ");
                }
                output.append("\n");
            }
        }
        
        return output.toString();
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
            return new EvidenceAnalysis(service, "", "", List.of(), List.of(), 0, java.util.Map.of(), List.of());
        }
        
        // Pattern matching for common error types
        List<String> errorPatterns = new ArrayList<>();
        List<String> anomalies = new ArrayList<>();
        int errorCount = 0;
        java.util.Map<String,Integer> statusCounts = new java.util.HashMap<>();
        List<String> sampleErrorLines = new ArrayList<>();
        
        // Common error patterns
        Pattern errorPattern = Pattern.compile("(?i)(error|exception|failed|timeout|refused)");
        Pattern httpErrorPattern = Pattern.compile("(?i)(5\\d{2}|4\\d{2})");
        Pattern dbErrorPattern = Pattern.compile("(?i)(connection.*refused|deadlock|timeout.*database)");
        
        String[] lines = logs.split("\\n");
        for (String line : lines) {
            if (errorPattern.matcher(line).find()) {
                errorCount++;
                if (sampleErrorLines.size() < 5) {
                    sampleErrorLines.add(line.trim());
                }
            }
            
            Matcher httpMatcher = httpErrorPattern.matcher(line);
            if (httpMatcher.find()) {
                String code = httpMatcher.group(1);
                errorPatterns.add("HTTP " + code + " errors");
                statusCounts.merge(code, 1, Integer::sum);
            }
            
            if (dbErrorPattern.matcher(line).find()) {
                errorPatterns.add("Database connectivity issues");
            }
        }
        
        // Detect anomalies
        if (errorCount > lines.length * 0.1) {
            anomalies.add("High error rate (" + errorCount + " errors in " + lines.length + " lines)");
        }
        
        return new EvidenceAnalysis(service, logs, "", errorPatterns, anomalies, errorCount, statusCounts, sampleErrorLines);
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
    
}

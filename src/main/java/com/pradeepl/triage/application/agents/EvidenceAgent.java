package com.pradeepl.triage.application.agents;

import akka.javasdk.annotations.Component;
import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.RemoteMcpTools;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "evidence-agent")
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
            "Please develop a comprehensive evidence collection and analysis strategy for this incident.\n\n" +
            "IMPORTANT: You have access to MCP tools for evidence gathering:\n" +
            "- fetch_logs: Call with service name and number of lines to fetch recent logs\n" +
            "- query_metrics: Call with metrics expression and time range to get performance data\n" +
            "- correlate_evidence: Call with log and metric findings to identify correlations\n\n" +
            "Use these tools to gather evidence, then provide your structured analysis in JSON format.",
            req.service() != null ? req.service() : "unknown",
            req.metricsExpr() != null ? req.metricsExpr() : "errors:rate5m",
            req.range() != null ? req.range() : "1h",
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );

        logger.debug("EvidenceAgent prompt length: {} chars", contextualPrompt.length());
        logger.info("🔍 Using native MCP tools from evidence-mcp-server (port 9200)");

        return effects()
                .model(
                        ModelProvider.openAi()
                                .withApiKey(System.getenv("OPENAI_API_KEY"))
                                .withModelName("gpt-4o-mini")
                                .withTemperature(0.3)
                                .withMaxTokens(2000)
                )
                .memory(MemoryProvider.limitedWindow())
                .mcpTools(
                        RemoteMcpTools.fromService("evidence-mcp-server")
                                .withAllowedToolNames("fetch_logs", "query_metrics", "correlate_evidence")
                )
                .systemMessage(SYSTEM)
                .userMessage(contextualPrompt)
                .thenReply();
    }
}

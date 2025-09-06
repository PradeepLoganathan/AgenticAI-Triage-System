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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ComponentId("triage-agent")
public class TriageAgent extends Agent {
    
    private static final Logger logger = LoggerFactory.getLogger(TriageAgent.class);

    private static final String SYSTEM = """
        You are an expert incident triage specialist with deep knowledge of distributed systems, 
        troubleshooting methodologies, and production incident response.
        
        SYSTEMATIC DIAGNOSIS PROCESS:
        
        1. INITIAL ASSESSMENT:
           - Analyze the incident classification and evidence
           - Assess immediate business impact and urgency
           - Identify affected systems and user impact scope
        
        2. ROOT CAUSE ANALYSIS using structured approaches:
           - Apply 5 Whys methodology for systematic drilling down
           - Consider common failure patterns: cascading failures, resource exhaustion, 
             configuration errors, deployment issues, external dependencies
           - Examine temporal correlations in evidence
           - Distinguish between symptoms and root causes
        
        3. HYPOTHESIS FORMATION:
           - Develop primary hypothesis based on strongest evidence
           - Consider alternative hypotheses
           - Assess confidence level for each hypothesis
           - Identify information gaps that affect confidence
        
        4. ACTION PRIORITIZATION:
           - Immediate mitigation actions (stop the bleeding)
           - Diagnostic actions (validate hypotheses)
           - Communication actions (stakeholder updates)
           - Prevention actions (longer-term fixes)
        
        Use available tools for additional investigation when confidence is low or 
        critical information is missing.
        
        Return structured JSON analysis:
        {
          "impact_assessment": {
            "severity": "P1|P2|P3|P4",
            "affected_users": "description",
            "business_impact": "description",
            "urgency_factors": ["factor1", "factor2"]
          },
          "root_cause_analysis": {
            "primary_hypothesis": {
              "hypothesis": "detailed explanation",
              "confidence": 8,
              "supporting_evidence": ["evidence1", "evidence2"],
              "reasoning": "why this is most likely"
            },
            "alternative_hypotheses": [
              {"hypothesis": "alternative explanation", "confidence": 3, "reasoning": "why less likely"}
            ],
            "five_whys_analysis": {
              "why1": "immediate cause",
              "why2": "underlying cause",
              "why3": "deeper cause",
              "why4": "systemic cause",
              "why5": "root organizational cause"
            }
          },
          "action_plan": {
            "immediate_actions": [
              {"action": "stop the bleeding action", "priority": "CRITICAL", "owner": "who", "eta": "5min"}
            ],
            "diagnostic_actions": [
              {"action": "validate hypothesis action", "priority": "HIGH", "owner": "who", "eta": "10min"}
            ],
            "communication_actions": [
              {"action": "notify stakeholders", "priority": "HIGH", "owner": "who", "eta": "immediate"}
            ],
            "preventive_actions": [
              {"action": "prevent recurrence", "priority": "MEDIUM", "owner": "who", "eta": "post-incident"}
            ]
          },
          "information_gaps": ["what additional info would increase confidence"],
          "recommended_escalation": "when and to whom to escalate",
          "monitoring_focus": ["key metrics to watch during resolution"]
        }
        """;

    public record Request(String incident) {}

    public Effect<String> triage(Request request) {
        logger.info("🔬 TriageAgent.triage() - Starting OpenAI call for systematic diagnosis");
        logger.debug("TriageAgent input context length: {} characters", request.incident() != null ? request.incident().length() : 0);
        
        String contextualPrompt = String.format(
            "INCIDENT TRIAGE REQUEST\n" +
            "========================\n" +
            "Timestamp: %s\n\n" +
            "Context to analyze:\n%s\n\n" +
            "Please perform a systematic triage analysis using the structured diagnosis process. " +
            "If you need additional information to increase confidence in your analysis, " +
            "use the available tools to gather more evidence.",
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            request.incident()
        );
        
        logger.debug("TriageAgent sending prompt to OpenAI (length: {} chars)", contextualPrompt.length());
        
        return effects()
            .model(
                ModelProvider.openAi()
                    .withApiKey(System.getenv("OPENAI_API_KEY"))
                    .withModelName("gpt-4o-mini")
                    .withTemperature(0.3)
                    .withMaxTokens(2500)
            )
            .memory(MemoryProvider.limitedWindow())
            .tools(this)
            .systemMessage(SYSTEM)
            .userMessage(contextualPrompt)
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
    
    @FunctionTool(name = "assess_impact", description = "Assess business and technical impact of the incident")
    public String assessImpact(
            @Description("Description of affected systems or services") String affectedSystems,
            @Description("Estimated number or percentage of affected users") String userImpact
    ) {
        List<String> impactFactors = new ArrayList<>();
        
        // Analyze system criticality
        if (affectedSystems != null) {
            String systems = affectedSystems.toLowerCase();
            if (systems.contains("payment") || systems.contains("checkout") || systems.contains("auth")) {
                impactFactors.add("Critical business function affected");
            }
            if (systems.contains("database") || systems.contains("api-gateway") || systems.contains("load-balancer")) {
                impactFactors.add("Core infrastructure component affected");
            }
        }
        
        // Analyze user impact
        if (userImpact != null) {
            String impact = userImpact.toLowerCase();
            if (impact.contains("100%") || impact.contains("all")) {
                impactFactors.add("Complete service outage");
            } else if (impact.contains("%") && impact.matches(".*[5-9][0-9]%.*")) {
                impactFactors.add("Majority of users affected");
            }
        }
        
        // Time-based factors
        int hour = LocalDateTime.now().getHour();
        if (hour >= 9 && hour < 17) {
            impactFactors.add("Incident during business hours - high visibility");
        }
        
        return String.format(
            "Impact Assessment:\n" +
            "- Affected Systems: %s\n" +
            "- User Impact: %s\n" +
            "- Impact Factors: %s\n" +
            "- Time Context: %s",
            affectedSystems != null ? affectedSystems : "Not specified",
            userImpact != null ? userImpact : "Not specified",
            String.join(", ", impactFactors),
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
    
    @FunctionTool(name = "analyze_patterns", description = "Analyze patterns in incident data to identify common failure modes")
    public String analyzePatterns(
            @Description("Incident symptoms and error patterns") String symptoms,
            @Description("Timing information about when issues started") String timing
    ) {
        List<String> patterns = new ArrayList<>();
        List<String> recommendations = new ArrayList<>();
        
        if (symptoms != null) {
            String s = symptoms.toLowerCase();
            
            // Common patterns
            if (s.contains("timeout") || s.contains("slow")) {
                patterns.add("Performance degradation pattern detected");
                recommendations.add("Check resource utilization (CPU, memory, disk I/O)");
                recommendations.add("Examine database query performance");
            }
            
            if (s.contains("5xx") || s.contains("500") || s.contains("503")) {
                patterns.add("Server error pattern detected");
                recommendations.add("Check service health endpoints");
                recommendations.add("Verify dependency availability");
            }
            
            if (s.contains("connection") && s.contains("refused")) {
                patterns.add("Connectivity issue pattern detected");
                recommendations.add("Check network connectivity");
                recommendations.add("Verify service discovery/load balancer configuration");
            }
            
            if (s.contains("memory") || s.contains("oom")) {
                patterns.add("Memory exhaustion pattern detected");
                recommendations.add("Check for memory leaks");
                recommendations.add("Review recent deployments for memory usage changes");
            }
        }
        
        if (timing != null && timing.contains("deploy")) {
            patterns.add("Deployment correlation detected");
            recommendations.add("Consider rollback as immediate mitigation");
            recommendations.add("Compare deployment changes with error patterns");
        }
        
        return String.format(
            "Pattern Analysis:\n" +
            "- Detected Patterns: %s\n" +
            "- Recommendations: %s",
            patterns.isEmpty() ? "No specific patterns identified" : String.join(", ", patterns),
            recommendations.isEmpty() ? "Standard incident response procedures" : String.join("; ", recommendations)
        );
    }
}

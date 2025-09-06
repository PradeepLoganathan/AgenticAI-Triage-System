package com.example.sample.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.annotations.Description;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;

@ComponentId("classifier-agent")
public class ClassifierAgent extends Agent {

    private static final String SYSTEM = """
        You are an expert incident classifier with deep knowledge of distributed systems.
        
        Follow this structured approach:
        
        1. ANALYZE the incident systematically:
           - Identify key symptoms and error patterns
           - Extract system components and services mentioned
           - Assess scope of impact (users, transactions, systems)
           - Note temporal patterns (when did it start, frequency)
        
        2. CLASSIFY with reasoning:
           - Service: Match to known services or infer from context
           - Severity: P1 (critical outage), P2 (major degradation), P3 (minor issues), P4 (cosmetic)
           - Domain: Core business function affected
        
        3. ASSESS confidence and validate:
           - Rate your confidence (1-10) for each classification
           - Identify any ambiguities or missing information
           - Consider alternative interpretations
        
        Use the service validation tool if you're unsure about service names.
        
        Return structured JSON with your analysis:
        {
          "timestamp": "current_time",
          "analysis": {
            "symptoms": ["symptom1", "symptom2"],
            "affected_systems": ["system1", "system2"],
            "impact_scope": "description of who/what is affected",
            "temporal_pattern": "when and how often"
          },
          "classification": {
            "service": "service-name",
            "severity": "P1|P2|P3|P4",
            "domain": "functional-domain"
          },
          "confidence": {
            "service": 8,
            "severity": 9,
            "domain": 7,
            "overall": 8
          },
          "reasoning": "detailed explanation of classification decisions",
          "alternative_interpretations": ["other possible classifications if any"],
          "information_gaps": ["what additional info would help"]
        }
        """;

    public record Request(String incident) {}
    
    private static final Set<String> KNOWN_SERVICES = Set.of(
        "user-service", "auth-service", "payment-service", "order-service",
        "inventory-service", "notification-service", "catalog-service",
        "checkout-service", "shipping-service", "review-service",
        "recommendation-service", "search-service", "api-gateway",
        "load-balancer", "database", "cache-service", "message-queue"
    );

    public Effect<String> classify(Request req) {
        String contextualPrompt = String.format(
            "Current time: %s\n\nIncident to classify:\n%s\n\nPlease analyze and classify this incident following the structured approach.",
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            req.incident()
        );
        
        return effects()
                .model(
                        ModelProvider.openAi()
                                .withApiKey(System.getenv("OPENAI_API_KEY"))
                                .withModelName("gpt-4o-mini")
                                .withTemperature(0.2)
                                .withMaxTokens(1200)
                )
                .memory(MemoryProvider.limitedWindow())
                .tools(this)
                .systemMessage(SYSTEM)
                .userMessage(contextualPrompt)
                .thenReply();
    }
    
    @FunctionTool(name = "validate_service", description = "Validate if a service name exists in the known services list")
    public String validateService(
            @Description("Service name to validate") String serviceName
    ) {
        if (serviceName == null || serviceName.trim().isEmpty()) {
            return "Invalid service name provided";
        }
        
        String normalized = serviceName.toLowerCase().trim();
        boolean exists = KNOWN_SERVICES.contains(normalized);
        
        if (exists) {
            return String.format("Service '%s' is a known service", serviceName);
        } else {
            // Find closest matches
            var suggestions = KNOWN_SERVICES.stream()
                .filter(s -> s.contains(normalized) || normalized.contains(s.replace("-service", "")))
                .limit(3)
                .toList();
            
            if (suggestions.isEmpty()) {
                return String.format("Service '%s' is not in known services list. This might be a new service or misnamed.", serviceName);
            } else {
                return String.format("Service '%s' not found. Did you mean: %s", serviceName, String.join(", ", suggestions));
            }
        }
    }
    
    @FunctionTool(name = "get_current_context", description = "Get current system context like time of day, typical load patterns")
    public String getCurrentContext() {
        LocalDateTime now = LocalDateTime.now();
        int hour = now.getHour();
        
        String timeContext;
        if (hour >= 9 && hour < 17) {
            timeContext = "Business hours - High traffic expected";
        } else if (hour >= 17 && hour < 22) {
            timeContext = "Evening - Moderate traffic, common deployment window";
        } else {
            timeContext = "Off hours - Low traffic, maintenance window";
        }
        
        return String.format("Current time: %s (%s). Day of week: %s", 
            now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            timeContext,
            now.getDayOfWeek()
        );
    }
}


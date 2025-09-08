package com.example.sample.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.annotations.Description;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.InputStream;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;


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
        
        **IMPORTANT**: Before classifying, ALWAYS call get_known_services to see all available services. 
        This ensures accurate service identification and prevents misclassification.
        
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
    
   
    public Effect<String> classify(Request req) {
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
        
        String contextualPrompt = String.format(
            "Current time: %s (%s, %s)\n\nIncident to classify:\n%s\n\nPlease analyze and classify this incident following the structured approach.",
            now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            timeContext,
            now.getDayOfWeek(),
            req.incident()
        );
        
        return effects()
                .model(
                        ModelProvider.openAi()
                                .withApiKey(System.getenv("OPENAI_API_KEY"))
                                .withModelName("gpt-4o-mini")
                                .withTemperature(0.2)
                                .withMaxTokens(1500)
                )
                .memory(MemoryProvider.limitedWindow())
                .tools(this)
                .systemMessage(SYSTEM)
                .userMessage(contextualPrompt)
                .thenReply();
    }
    
    @FunctionTool(name = "get_known_services", description = "Get the complete list of known services to choose from for accurate classification")
    public String getKnownServices() {
        try {
            ObjectMapper mapper = new ObjectMapper();
            InputStream inputStream = getClass().getClassLoader().getResourceAsStream("services.json");
            
            if (inputStream == null) {
                return "Error: services.json configuration file not found";
            }
            
            JsonNode config = mapper.readTree(inputStream);
            
            // Build services list
            List<String> services = mapper.convertValue(
                config.get("services"), 
                mapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );
            
            // Build categories section
            StringBuilder response = new StringBuilder();
            response.append("Known Services List:\n");
            response.append(String.join(", ", services));
            response.append("\n\nService Categories:\n");
            
            JsonNode categories = config.get("categories");
            categories.fieldNames().forEachRemaining(categoryName -> {
                List<String> categoryServices = mapper.convertValue(
                    categories.get(categoryName),
                    mapper.getTypeFactory().constructCollectionType(List.class, String.class)
                );
                response.append(String.format("- %s: %s\n", categoryName, String.join(", ", categoryServices)));
            });
            
            // Add usage instructions
            response.append("\n");
            response.append(config.get("usage_instructions").asText());
            
            return response.toString();
            
        } catch (IOException e) {
            return "Error loading services configuration: " + e.getMessage();
        }
    }
}


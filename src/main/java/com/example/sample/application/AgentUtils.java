package com.example.sample.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.List;
import java.util.ArrayList;

/**
 * Utility class for agent operations including JSON parsing, validation, and data extraction.
 */
public class AgentUtils {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Extract service name from classification JSON with fallback logic
     */
    public static String extractServiceFromClassification(String classificationJson) {
        if (classificationJson == null || classificationJson.trim().isEmpty()) {
            return "unknown";
        }
        
        try {
            JsonNode node = objectMapper.readTree(classificationJson);
            
            // Try to get from classification object first
            if (node.has("classification") && node.get("classification").has("service")) {
                return node.get("classification").get("service").asText();
            }
            
            // Fallback to direct service field
            if (node.has("service")) {
                return node.get("service").asText();
            }
            
        } catch (Exception e) {
            // If JSON parsing fails, try regex extraction as fallback
            return extractServiceWithRegex(classificationJson);
        }
        
        return "unknown";
    }
    
    /**
     * Extract confidence scores from agent responses
     */
    public static double extractConfidenceScore(String agentResponse, String scoreType) {
        if (agentResponse == null) return 0.0;
        
        try {
            JsonNode node = objectMapper.readTree(agentResponse);
            
            // Look for confidence object
            if (node.has("confidence")) {
                JsonNode confidence = node.get("confidence");
                if (confidence.has(scoreType)) {
                    return confidence.get(scoreType).asDouble();
                }
                if (confidence.has("overall")) {
                    return confidence.get("overall").asDouble();
                }
            }
            
            // Look for confidence_assessment
            if (node.has("confidence_assessment")) {
                JsonNode assessment = node.get("confidence_assessment");
                if (assessment.has(scoreType)) {
                    return assessment.get(scoreType).asDouble();
                }
            }
            
        } catch (Exception e) {
            // Fallback to regex parsing
            Pattern pattern = Pattern.compile("\"" + scoreType + "\"\\s*:\\s*(\\d+(?:\\.\\d+)?)");
            Matcher matcher = pattern.matcher(agentResponse);
            if (matcher.find()) {
                return Double.parseDouble(matcher.group(1));
            }
        }
        
        return 0.0;
    }
    
    /**
     * Extract key findings from evidence analysis
     */
    public static List<String> extractKeyFindings(String evidenceResponse) {
        List<String> findings = new ArrayList<>();
        
        if (evidenceResponse == null) return findings;
        
        try {
            JsonNode node = objectMapper.readTree(evidenceResponse);
            
            if (node.has("analysis") && node.get("analysis").has("key_findings")) {
                JsonNode keyFindings = node.get("analysis").get("key_findings");
                if (keyFindings.isArray()) {
                    for (JsonNode finding : keyFindings) {
                        findings.add(finding.asText());
                    }
                }
            }
            
        } catch (Exception e) {
            // Fallback: look for common patterns in text
            if (evidenceResponse.contains("key findings") || evidenceResponse.contains("Key findings")) {
                // Extract bullet points or numbered items after "key findings"
                Pattern pattern = Pattern.compile("(?:key findings|Key findings)[^\\n]*\\n([^}]*)");
                Matcher matcher = pattern.matcher(evidenceResponse);
                if (matcher.find()) {
                    String findingsText = matcher.group(1);
                    Pattern itemPattern = Pattern.compile("[-*•]\\s*([^\\n]+)");
                    Matcher itemMatcher = itemPattern.matcher(findingsText);
                    while (itemMatcher.find()) {
                        findings.add(itemMatcher.group(1).trim());
                    }
                }
            }
        }
        
        return findings;
    }
    
    /**
     * Validate JSON structure of agent responses
     */
    public static boolean isValidJson(String response) {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }
        
        try {
            objectMapper.readTree(response);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Extract severity from classification
     */
    public static String extractSeverity(String classificationJson) {
        if (classificationJson == null) return "P3";
        
        try {
            JsonNode node = objectMapper.readTree(classificationJson);
            
            if (node.has("classification") && node.get("classification").has("severity")) {
                return node.get("classification").get("severity").asText();
            }
            
            if (node.has("severity")) {
                return node.get("severity").asText();
            }
            
        } catch (Exception e) {
            // Regex fallback
            Pattern pattern = Pattern.compile("\"severity\"\\s*:\\s*\"(P[1-4])\"");
            Matcher matcher = pattern.matcher(classificationJson);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        return "P3"; // Default severity
    }
    
    /**
     * Determine if incident requires immediate escalation
     */
    public static boolean requiresImmediateEscalation(String classificationJson, String evidenceResponse) {
        String severity = extractSeverity(classificationJson);
        double confidence = extractConfidenceScore(classificationJson, "overall");
        
        // P1 incidents always require escalation
        if ("P1".equals(severity)) {
            return true;
        }
        
        // High confidence P2 incidents during business hours
        if ("P2".equals(severity) && confidence >= 8.0) {
            int hour = java.time.LocalDateTime.now().getHour();
            if (hour >= 9 && hour < 17) {
                return true;
            }
        }
        
        // Check for high-risk keywords in evidence
        if (evidenceResponse != null) {
            String evidence = evidenceResponse.toLowerCase();
            if (evidence.contains("security") || evidence.contains("breach") || 
                evidence.contains("data loss") || evidence.contains("payment")) {
                return true;
            }
        }
        
        return false;
    }
    
    private static String extractServiceWithRegex(String classificationJson) {
        // Regex patterns to find service name
        Pattern[] patterns = {
            Pattern.compile("\"service\"\\s*:\\s*\"([^\"]+)\""),
            Pattern.compile("service[^:]*:\\s*([a-zA-Z-_]+)"),
            Pattern.compile("\"([a-zA-Z-_]+)\"\\s*service")
        };
        
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(classificationJson);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        
        return "unknown";
    }
    
    /**
     * Format time for display in agent responses
     */
    public static String formatTimestamp(java.time.LocalDateTime dateTime) {
        return dateTime.format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
    
    /**
     * Clean and normalize agent response text
     */
    public static String cleanResponse(String response) {
        if (response == null) return "";
        
        // Remove excessive whitespace
        response = response.replaceAll("\\s+", " ");
        
        // Remove markdown-style formatting if present
        response = response.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        response = response.replaceAll("\\*([^*]+)\\*", "$1");
        
        return response.trim();
    }
}
package com.pradeepl.triage.application;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.annotations.Component;
import akka.javasdk.annotations.FunctionTool;
import akka.javasdk.annotations.Description;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

@Component(id = "summary-agent")
public class SummaryAgent extends Agent {

    private static final String SYSTEM = """
        You are an expert incident communications specialist skilled in creating clear, 
        actionable summaries for different audiences and stakeholders.
        
        COMMUNICATION PRINCIPLES:
        
        1. AUDIENCE ADAPTATION:
           - Technical teams need detailed technical context
           - Executive leadership needs business impact and timelines
           - Customer support needs customer-facing explanations
           - External communications need appropriate transparency
        
        2. INFORMATION HIERARCHY:
           - Lead with impact and current status
           - Provide clear timeline of events
           - Explain root cause in appropriate detail
           - Outline next steps with ownership and timelines
           - Include lessons learned and prevention measures
        
        3. COMMUNICATION QUALITY:
           - Use clear, jargon-free language appropriate to audience
           - Include quantifiable impact where possible
           - Provide realistic timelines with confidence levels
           - Balance transparency with reassurance
           - Include contact information for follow-up
        
        Use available tools to tailor communications for different audiences.
        
        Return structured JSON with multiple communication formats:
        {
          "executive_summary": {
            "headline": "brief incident description",
            "business_impact": "quantified impact on users/revenue",
            "current_status": "what's happening now",
            "resolution_timeline": "when will this be resolved",
            "next_update": "when is next communication",
            "executive_actions_needed": ["any leadership decisions required"]
          },
          "technical_summary": {
            "incident_overview": "technical description",
            "root_cause_analysis": "detailed technical cause",
            "systems_affected": ["service1", "service2"],
            "remediation_steps": ["step1", "step2"],
            "monitoring_focus": ["metrics to watch"],
            "technical_contacts": ["who to reach for details"]
          },
          "customer_facing_summary": {
            "public_description": "customer-appropriate incident description",
            "user_impact": "what customers experienced",
            "resolution_status": "progress toward resolution",
            "customer_actions": "any actions customers should take",
            "support_information": "how to get help"
          },
          "post_incident_preview": {
            "lessons_learned": ["key insights from this incident"],
            "prevention_measures": ["how to prevent recurrence"],
            "process_improvements": ["what to improve in response"],
            "follow_up_actions": ["post-incident tasks"]
          },
          "timeline": [
            {"time": "timestamp", "event": "what happened", "impact": "effect"}
          ]
        }
        """;

    public record Request(String incident, String classificationJson, String triageText, String remediationText) {}
    
    public enum AudienceType {
        EXECUTIVE, TECHNICAL, CUSTOMER_SUPPORT, PUBLIC, INTERNAL_ALL
    }
    
    private static final Set<String> HIGH_VISIBILITY_KEYWORDS = Set.of(
        "payment", "checkout", "auth", "login", "security", "data-loss",
        "outage", "downtime", "breach", "financial"
    );

    public Effect<String> summarize(Request req) {
        // Pre-compute guidance using available tool methods so their outputs
        // are explicitly incorporated into the model prompt.
        String urgencyAssessment = assessCommunicationUrgency(
                req.incident() != null ? req.incident() : "",
                req.classificationJson() != null ? req.classificationJson() : "");

        String initialTimeline = generateTimeline(
                req.classificationJson() != null ? req.classificationJson() : "",
                req.triageText() != null ? req.triageText() : "",
                req.remediationText() != null ? req.remediationText() : "");

        String executiveToneGuidance = tailorMessageForAudience(
                "EXECUTIVE",
                req.incident() != null ? req.incident() : "");

        String publicToneGuidance = tailorMessageForAudience(
                "PUBLIC",
                req.incident() != null ? req.incident() : "");

        String contextualPrompt = String.format(
            "INCIDENT SUMMARY GENERATION REQUEST\n" +
            "===================================\n" +
            "Timestamp: %s\n\n" +
            "ORIGINAL INCIDENT:\n%s\n\n" +
            "CLASSIFICATION RESULTS:\n%s\n\n" +
            "TRIAGE ANALYSIS:\n%s\n\n" +
            "REMEDIATION PLAN:\n%s\n\n" +
            "TOOL OUTPUTS (PRE-COMPUTED FOR YOU):\n" +
            "------------------------------------\n" +
            "COMMUNICATION URGENCY ASSESSMENT:\n%s\n\n" +
            "INITIAL INCIDENT TIMELINE DRAFT:\n%s\n\n" +
            "AUDIENCE TONE GUIDANCE (EXECUTIVE):\n%s\n\n" +
            "AUDIENCE TONE GUIDANCE (PUBLIC):\n%s\n\n" +
            "Please create comprehensive summaries for different audiences. " +
            "Use available tools to assess appropriate communication strategies " +
            "and tailor messaging for each stakeholder group. " +
            "Incorporate the provided tool outputs into the structured JSON (and you may still call tools if needed).",
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            req.incident() != null ? req.incident() : "Not provided",
            req.classificationJson() != null ? req.classificationJson() : "Not provided",
            req.triageText() != null ? req.triageText() : "Not provided",
            req.remediationText() != null ? req.remediationText() : "Not provided",
            urgencyAssessment,
            initialTimeline,
            executiveToneGuidance,
            publicToneGuidance
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
    
    @FunctionTool(name = "assess_communication_urgency", description = "Assess urgency and appropriate communication channels for incident")
    public String assessCommunicationUrgency(
            @Description("Incident severity and description") String incidentDetails,
            @Description("Business impact description") String businessImpact
    ) {
        List<String> urgencyFactors = new ArrayList<>();
        String urgencyLevel = "MEDIUM";
        List<String> recommendedChannels = new ArrayList<>();
        
        if (incidentDetails != null) {
            String details = incidentDetails.toLowerCase();
            
            // Check for high-visibility keywords
            for (String keyword : HIGH_VISIBILITY_KEYWORDS) {
                if (details.contains(keyword)) {
                    urgencyFactors.add("High-visibility system affected: " + keyword);
                    urgencyLevel = "HIGH";
                }
            }
            
            // Check severity indicators
            if (details.contains("p1") || details.contains("critical")) {
                urgencyFactors.add("P1/Critical severity incident");
                urgencyLevel = "HIGH";
                recommendedChannels.add("Immediate executive notification");
                recommendedChannels.add("Customer communication within 30min");
            } else if (details.contains("p2") || details.contains("major")) {
                urgencyFactors.add("P2/Major severity incident");
                urgencyLevel = urgencyLevel.equals("LOW") ? "MEDIUM" : urgencyLevel;
                recommendedChannels.add("Executive notification within 1hr");
            }
        }
        
        if (businessImpact != null) {
            String impact = businessImpact.toLowerCase();
            if (impact.contains("revenue") || impact.contains("financial")) {
                urgencyFactors.add("Revenue/financial impact identified");
                urgencyLevel = "HIGH";
            }
            if (impact.contains("customer") && impact.contains("facing")) {
                urgencyFactors.add("Customer-facing impact");
                urgencyLevel = urgencyLevel.equals("LOW") ? "MEDIUM" : urgencyLevel;
            }
        }
        
        // Time-based factors
        int hour = LocalDateTime.now().getHour();
        if (hour >= 9 && hour < 17) {
            urgencyFactors.add("Business hours - full communication team available");
            recommendedChannels.add("Standard communication channels");
        } else {
            urgencyFactors.add("Off-hours - limited communication team");
            recommendedChannels.add("On-call communication lead");
        }
        
        // Default channels if none specified
        if (recommendedChannels.isEmpty()) {
            recommendedChannels.add("Standard incident communication process");
        }
        
        return String.format(
            "Communication Urgency Assessment:\n" +
            "- Overall Urgency: %s\n" +
            "- Urgency Factors: %s\n" +
            "- Recommended Channels: %s\n" +
            "- Time Context: %s",
            urgencyLevel,
            String.join("; ", urgencyFactors),
            String.join("; ", recommendedChannels),
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
    
    @FunctionTool(name = "tailor_message_for_audience", description = "Adapt message content and tone for specific audience")
    public String tailorMessageForAudience(
            @Description("Target audience type: EXECUTIVE, TECHNICAL, CUSTOMER_SUPPORT, PUBLIC") String audienceType,
            @Description("Core message content to adapt") String coreMessage
    ) {
        String adaptedMessage = "";
        
        try {
            AudienceType audience = AudienceType.valueOf(audienceType.toUpperCase());
            
            switch (audience) {
                case EXECUTIVE:
                    adaptedMessage = "Executive Summary:\n" +
                        "- Focus: Business impact, timeline, resource needs\n" +
                        "- Tone: Confident, solution-focused, quantifiable\n" +
                        "- Key elements: Financial impact, customer effect, resolution ETA\n" +
                        "- Avoid: Technical jargon, uncertain language\n" +
                        "- Include: Clear action items requiring executive decision";
                    break;
                case TECHNICAL:
                    adaptedMessage = "Technical Team Communication:\n" +
                        "- Focus: Root cause, technical details, remediation steps\n" +
                        "- Tone: Detailed, precise, technically accurate\n" +
                        "- Key elements: System components, error messages, metrics\n" +
                        "- Include: Debugging context, architectural implications\n" +
                        "- Provide: Specific technical contacts and resources";
                    break;
                case CUSTOMER_SUPPORT:
                    adaptedMessage = "Customer Support Brief:\n" +
                        "- Focus: Customer impact, workarounds, support responses\n" +
                        "- Tone: Empathetic, helpful, solution-oriented\n" +
                        "- Key elements: What customers see, how to help them\n" +
                        "- Include: Escalation paths, known workarounds\n" +
                        "- Avoid: Technical details that don't help customers";
                    break;
                case PUBLIC:
                    adaptedMessage = "Public Communication Guidelines:\n" +
                        "- Focus: Transparency, accountability, customer care\n" +
                        "- Tone: Professional, apologetic where appropriate, reassuring\n" +
                        "- Key elements: Acknowledgment, impact, resolution progress\n" +
                        "- Avoid: Technical jargon, blame, uncertain timelines\n" +
                        "- Include: Next update timeline, customer actions";
                    break;
                default:
                    adaptedMessage = "General audience adaptation completed.";
            }
            
        } catch (IllegalArgumentException e) {
            adaptedMessage = "Unknown audience type. Using general guidelines.";
        }
        
        return String.format(
            "Message Adaptation for %s Audience:\n\n" +
            "%s\n\n" +
            "Core Message Context:\n%s",
            audienceType.toUpperCase(),
            adaptedMessage,
            coreMessage != null ? coreMessage : "No core message provided"
        );
    }
    
    @FunctionTool(name = "generate_timeline", description = "Create incident timeline from available information")
    public String generateTimeline(
            @Description("Classification information with timing") String classificationInfo,
            @Description("Evidence and investigation details") String evidenceInfo,
            @Description("Remediation actions and timelines") String remediationInfo
    ) {
        List<String> timelineEvents = new ArrayList<>();
        
        // Try to extract timeline information from the provided context
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        // Add standard timeline events
        timelineEvents.add("T-X: Incident symptoms first appeared (exact time TBD)");
        timelineEvents.add("T-Y: Incident detected/reported (from monitoring or user reports)");
        timelineEvents.add("T-Z: Incident response team engaged");
        timelineEvents.add("T-0: Triage and classification completed");
        timelineEvents.add("T+0: Evidence collection initiated");
        timelineEvents.add("T+10: Root cause analysis completed");
        timelineEvents.add("T+15: Remediation plan finalized");
        timelineEvents.add("Current: " + currentTime);
        
        // Try to add specific events from provided information
        if (classificationInfo != null && classificationInfo.toLowerCase().contains("deploy")) {
            timelineEvents.add("Related: Recent deployment identified as potential cause");
        }
        
        if (remediationInfo != null) {
            String remediation = remediationInfo.toLowerCase();
            if (remediation.contains("rollback")) {
                timelineEvents.add("Planned: Rollback execution");
            }
            if (remediation.contains("restart")) {
                timelineEvents.add("Planned: Service restart");
            }
        }
        
        return String.format(
            "Incident Timeline:\n" +
            "=================\n" +
            "%s\n\n" +
            "Note: This timeline should be refined with actual timestamps as they become available.\n" +
            "Classification Context: %s\n" +
            "Evidence Context: %s\n" +
            "Remediation Context: %s",
            String.join("\n", timelineEvents),
            classificationInfo != null ? classificationInfo : "None provided",
            evidenceInfo != null ? evidenceInfo : "None provided",
            remediationInfo != null ? remediationInfo : "None provided"
        );
    }
}

package com.example.sample.application;

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

@Component(id = "remediation-agent")
public class RemediationAgent extends Agent {

    private static final String SYSTEM = """
        You are a senior production remediation specialist with expertise in incident response, 
        risk management, and staged deployment strategies.
        
        REMEDIATION FRAMEWORK:
        
        1. RISK ASSESSMENT:
           - Identify all potential risks of proposed actions
           - Assess blast radius and impact of each remediation step
           - Consider dependencies and cascading effects
           - Evaluate rollback complexity and safety
        
        2. STAGED REMEDIATION PLANNING:
           - Design progressive rollout with validation gates
           - Plan for immediate, short-term, and long-term actions
           - Include detailed rollback procedures for each step
           - Specify success criteria and failure thresholds
        
        3. RESOURCE AND DEPENDENCY PLANNING:
           - Identify required personnel and their roles
           - Map system dependencies and coordination needs
           - Estimate time requirements for each phase
           - Plan communication and approval workflows
        
        4. VALIDATION AND MONITORING:
           - Define health checks and monitoring for each step
           - Specify metrics to watch during execution
           - Plan automated and manual validation procedures
           - Design alerting for remediation failures
        
        **IMPORTANT**: Before formulating a plan, you MUST use the `search_knowledge_base` tool to find existing runbooks or incident reports related to the service and symptoms. This is critical for creating a safe and effective plan.

        Use other available tools to validate assumptions and gather additional context.
        
        Return comprehensive JSON remediation plan:
        {
          "risk_assessment": {
            "high_risk_factors": ["factor1", "factor2"],
            "medium_risk_factors": ["factor3", "factor4"],
            "mitigation_strategies": ["strategy1", "strategy2"],
            "blast_radius": "scope of potential impact",
            "rollback_complexity": "LOW|MEDIUM|HIGH"
          },
          "staged_plan": {
            "immediate_actions": [
              {
                "step": 1,
                "action": "detailed action description",
                "purpose": "why this step",
                "risk_level": "LOW|MEDIUM|HIGH",
                "estimated_time": "5min",
                "owner": "role/team",
                "success_criteria": "how to know it worked",
                "rollback_procedure": "how to undo if needed",
                "dependencies": ["what needs to be ready first"]
              }
            ],
            "short_term_actions": [],
            "long_term_actions": []
          },
          "validation_strategy": {
            "health_checks": ["check1", "check2"],
            "key_metrics": ["metric1", "metric2"],
            "automated_validations": ["validation1", "validation2"],
            "manual_verifications": ["verification1", "verification2"]
          },
          "communication_plan": {
            "stakeholder_notifications": ["who to notify when"],
            "status_updates": "how often and to whom",
            "escalation_triggers": ["when to escalate"]
          },
          "contingency_planning": {
            "failure_scenarios": ["what could go wrong"],
            "alternative_approaches": ["other remediation options"],
            "emergency_contacts": ["who to call for help"]
          }
        }
        """;

    public record Request(String incident, String classificationJson, String evidenceJson, String triageText, String knowledgeBaseResult) {}
    
    private static final Set<String> HIGH_RISK_KEYWORDS = Set.of(
        "database", "payment", "auth", "user-data", "financial", "security",
        "production", "critical", "primary", "master", "leader"
    );
    
    private static final Set<String> ROLLBACK_FRIENDLY_ACTIONS = Set.of(
        "feature-flag", "config-change", "deployment", "routing", "scaling"
    );

    

    public Effect<String> remediate(Request req) {
        String contextualPrompt = String.format(
            "REMEDIATION PLANNING REQUEST\n" +
            "============================\n" +
            "Timestamp: %s\n\n" +
            "INCIDENT SUMMARY:\n%s\n\n" +
            "CLASSIFICATION:\n%s\n\n" +
            "EVIDENCE COLLECTED:\n%s\n\n" +
            "TRIAGE ANALYSIS:\n%s\n\n" +
            "KNOWLEDGE BASE SEARCH RESULTS:\n%s\n\n" +
            "Please develop a comprehensive, risk-aware remediation plan using the staged framework. " +
            "Use available tools to assess risks and validate your remediation approach.",
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            req.incident() != null ? req.incident() : "Not provided",
            req.classificationJson() != null ? req.classificationJson() : "Not provided",
            req.evidenceJson() != null ? req.evidenceJson() : "Not provided",
            req.triageText() != null ? req.triageText() : "Not provided",
            req.knowledgeBaseResult() != null ? req.knowledgeBaseResult() : "Not provided"
        );

        return effects()
                .model(
                        ModelProvider.openAi()
                                .withApiKey(System.getenv("OPENAI_API_KEY"))
                                .withModelName("gpt-4o-mini")
                                .withTemperature(0.2)
                                .withMaxTokens(2500)
                )
                .memory(MemoryProvider.limitedWindow())
                .tools(this)
                .systemMessage(SYSTEM)
                .userMessage(contextualPrompt)
                .thenReply();
    }
    
    @FunctionTool(name = "assess_remediation_risk", description = "Assess risk levels for proposed remediation actions")
    public String assessRemediationRisk(
            @Description("Description of the proposed remediation action") String action,
            @Description("Systems or components that would be affected") String affectedSystems
    ) {
        List<String> riskFactors = new ArrayList<>();
        String riskLevel = "LOW";
        
        if (action != null) {
            String actionLower = action.toLowerCase();
            
            // Check for high-risk actions
            if (actionLower.contains("restart") || actionLower.contains("reboot")) {
                riskFactors.add("System restart required - potential service interruption");
                riskLevel = "MEDIUM";
            }
            if (actionLower.contains("database") && actionLower.contains("schema")) {
                riskFactors.add("Database schema changes - high risk of data issues");
                riskLevel = "HIGH";
            }
            if (actionLower.contains("rollback") && actionLower.contains("deployment")) {
                riskFactors.add("Deployment rollback - generally safe but may lose recent features");
                riskLevel = "LOW";
            }
            if (actionLower.contains("config") && !actionLower.contains("rollback")) {
                riskFactors.add("Configuration change - medium risk, needs validation");
                riskLevel = "MEDIUM";
            }
        }
        
        if (affectedSystems != null) {
            String systemsLower = affectedSystems.toLowerCase();
            for (String highRiskKeyword : HIGH_RISK_KEYWORDS) {
                if (systemsLower.contains(highRiskKeyword)) {
                    riskFactors.add("Affects critical system: " + highRiskKeyword);
                    riskLevel = riskLevel.equals("LOW") ? "MEDIUM" : "HIGH";
                }
            }
        }
        
        // Time-based risk assessment
        int hour = LocalDateTime.now().getHour();
        if (hour >= 9 && hour < 17) {
            riskFactors.add("Business hours execution - higher visibility and impact");
        } else {
            riskFactors.add("Off-hours execution - lower user impact but limited support availability");
        }
        
        return String.format(
            "Risk Assessment for Remediation Action:\n" +
            "- Action: %s\n" +
            "- Affected Systems: %s\n" +
            "- Overall Risk Level: %s\n" +
            "- Risk Factors: %s\n" +
            "- Time Context: %s",
            action != null ? action : "Not specified",
            affectedSystems != null ? affectedSystems : "Not specified",
            riskLevel,
            String.join("; ", riskFactors),
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        );
    }
    
    @FunctionTool(name = "plan_rollback_strategy", description = "Plan detailed rollback procedures for remediation steps")
    public String planRollbackStrategy(
            @Description("The remediation action that needs rollback planning") String action,
            @Description("Current state before the action") String currentState
    ) {
        List<String> rollbackSteps = new ArrayList<>();
        String complexity = "MEDIUM";
        
        if (action != null) {
            String actionLower = action.toLowerCase();
            
            if (actionLower.contains("deployment") || actionLower.contains("deploy")) {
                rollbackSteps.add("Identify previous stable version");
                rollbackSteps.add("Execute deployment rollback via CI/CD pipeline");
                rollbackSteps.add("Verify service health post-rollback");
                rollbackSteps.add("Update load balancer routing if needed");
                complexity = "LOW";
            } else if (actionLower.contains("config")) {
                rollbackSteps.add("Restore previous configuration from backup");
                rollbackSteps.add("Restart affected services to reload config");
                rollbackSteps.add("Validate configuration changes took effect");
                complexity = "LOW";
            } else if (actionLower.contains("database")) {
                rollbackSteps.add("Stop application writes to database");
                rollbackSteps.add("Restore from point-in-time backup");
                rollbackSteps.add("Verify data integrity");
                rollbackSteps.add("Resume application traffic");
                complexity = "HIGH";
            } else if (actionLower.contains("scaling") || actionLower.contains("scale")) {
                rollbackSteps.add("Revert to previous instance count/size");
                rollbackSteps.add("Wait for auto-scaling policies to stabilize");
                rollbackSteps.add("Monitor resource utilization");
                complexity = "LOW";
            } else {
                rollbackSteps.add("Document current action effects");
                rollbackSteps.add("Reverse the action steps in inverse order");
                rollbackSteps.add("Validate system returned to previous state");
            }
        }
        
        return String.format(
            "Rollback Strategy:\n" +
            "- Action to Rollback: %s\n" +
            "- Rollback Complexity: %s\n" +
            "- Current State: %s\n" +
            "- Rollback Steps:\n%s\n" +
            "- Estimated Rollback Time: %s minutes",
            action != null ? action : "Not specified",
            complexity,
            currentState != null ? currentState : "Not documented",
            rollbackSteps.stream().map(step -> "  " + (rollbackSteps.indexOf(step) + 1) + ". " + step).reduce("", (a, b) -> a + "\n" + b),
            complexity.equals("LOW") ? "5-10" : complexity.equals("MEDIUM") ? "15-30" : "60+"
        );
    }
    
    @FunctionTool(name = "validate_resource_availability", description = "Check if required resources and personnel are available for remediation")
    public String validateResourceAvailability(
            @Description("List of required roles or teams for the remediation") String requiredRoles,
            @Description("Estimated duration of the remediation effort") String estimatedDuration
    ) {
        List<String> availability = new ArrayList<>();
        List<String> concerns = new ArrayList<>();
        
        int hour = LocalDateTime.now().getHour();
        int dayOfWeek = LocalDateTime.now().getDayOfWeek().getValue();
        
        // Time-based availability assessment
        if (hour >= 9 && hour < 17 && dayOfWeek <= 5) {
            availability.add("Business hours - Full team availability expected");
        } else if (hour >= 17 && hour < 22 && dayOfWeek <= 5) {
            availability.add("Evening hours - On-call team available, some specialists may be limited");
            concerns.add("Limited availability of specialized roles after hours");
        } else {
            availability.add("Off-hours/Weekend - On-call team only");
            concerns.add("Minimal staffing - consider deferring non-critical actions");
        }
        
        // Role-based availability
        if (requiredRoles != null) {
            String rolesLower = requiredRoles.toLowerCase();
            if (rolesLower.contains("dba") || rolesLower.contains("database")) {
                concerns.add("Database specialist required - ensure DBA availability");
            }
            if (rolesLower.contains("security")) {
                concerns.add("Security team involvement needed - may require approval delays");
            }
            if (rolesLower.contains("product")) {
                concerns.add("Product team input needed - may not be available off-hours");
            }
        }
        
        // Duration-based concerns
        if (estimatedDuration != null) {
            String durationLower = estimatedDuration.toLowerCase();
            if (durationLower.contains("hour") || durationLower.matches(".*[2-9][0-9]+.*min.*")) {
                concerns.add("Extended duration - ensure team coverage for full remediation window");
            }
        }
        
        return String.format(
            "Resource Availability Assessment:\n" +
            "- Current Time: %s\n" +
            "- Required Roles: %s\n" +
            "- Estimated Duration: %s\n" +
            "- Availability Status: %s\n" +
            "- Concerns: %s\n" +
            "- Recommendation: %s",
            LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
            requiredRoles != null ? requiredRoles : "Not specified",
            estimatedDuration != null ? estimatedDuration : "Not specified",
            String.join("; ", availability),
            concerns.isEmpty() ? "None identified" : String.join("; ", concerns),
            concerns.isEmpty() ? "Proceed with remediation" : "Address availability concerns before proceeding"
        );
    }
}

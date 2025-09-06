package com.example.sample.individual;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.sample.application.SummaryAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SummaryAgent Individual Tests")
public class SummaryAgentTest extends TestKitSupport {

    private final TestModelProvider summaryModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
                .withModelProvider(SummaryAgent.class, summaryModel);
    }

    @Test
    @DisplayName("Should generate multi-audience summaries with proper JSON structure")
    public void generateMultiAudienceSummaries() {
        String mockResponse = """
            {
              "executive_summary": {
                "headline": "Payment Service Outage - Complete Resolution Achieved",
                "business_impact": "$50,000 revenue impact, 45 minutes downtime",
                "current_status": "Service restored, monitoring for stability",
                "resolution_timeline": "Resolution completed at 15:15 UTC",
                "next_update": "Final post-incident report within 24 hours",
                "executive_actions_needed": ["Review deployment process", "Approve additional monitoring budget"]
              },
              "technical_summary": {
                "incident_overview": "Connection leak in v2.1.3 caused database pool exhaustion",
                "root_cause_analysis": "Missing connection cleanup in new payment method implementation",
                "systems_affected": ["payment-service", "payment-gateway", "database-cluster"],
                "remediation_steps": ["Rolled back to v2.1.2", "Scaled connection pool", "Applied hotfix"],
                "monitoring_focus": ["Database connections", "Error rates", "Response times"],
                "technical_contacts": ["john.doe@company.com (DevOps)", "jane.smith@company.com (DBA)"]
              },
              "customer_facing_summary": {
                "public_description": "Payment processing temporarily unavailable due to technical issue",
                "user_impact": "Customers unable to complete transactions for 45 minutes",
                "resolution_status": "Service fully restored, all transactions processing normally",
                "customer_actions": "No action required, failed transactions can be retried",
                "support_information": "Contact support@company.com for transaction issues"
              },
              "post_incident_preview": {
                "lessons_learned": ["Connection management in new code needs better testing", "Need faster rollback procedures"],
                "prevention_measures": ["Add connection leak detection", "Enhanced pre-production testing"],
                "process_improvements": ["Faster incident escalation", "Better monitoring alerts"],
                "follow_up_actions": ["Code review of v2.1.3 changes", "Update deployment checklist"]
              }
            }
            """;
        
        summaryModel.fixedResponse(mockResponse);

        String result = componentClient
                .forAgent()
                .inSession("test-session-summary")
                .method(SummaryAgent::summarize)
                .invoke(new SummaryAgent.Request(
                    "Payment service down for 45 minutes",
                    "{\"service\":\"payment\",\"severity\":\"P1\"}",
                    "Root cause: connection leak in deployment",
                    "Remediation: rollback + connection pool scaling"
                ));

        // Verify multi-audience structure
        assertThat(result).contains("executive_summary");
        assertThat(result).contains("technical_summary");
        assertThat(result).contains("customer_facing_summary");
        assertThat(result).contains("post_incident_preview");
        
        // Verify executive content
        assertThat(result).contains("business_impact");
        assertThat(result).contains("executive_actions_needed");
        assertThat(result).contains("resolution_timeline");
        
        // Verify technical content
        assertThat(result).contains("root_cause_analysis");
        assertThat(result).contains("systems_affected");
        assertThat(result).contains("technical_contacts");
        
        // Verify customer content
        assertThat(result).contains("public_description");
        assertThat(result).contains("user_impact");
        assertThat(result).contains("support_information");
    }

    @Test
    @DisplayName("Should adapt communication urgency based on incident severity")
    public void adaptCommunicationUrgency() {
        String mockResponse = """
            {
              "communication_urgency": {
                "overall_urgency": "HIGH",
                "urgency_factors": ["P1 severity", "Revenue impact", "Customer-facing service"],
                "recommended_channels": ["Immediate executive notification", "Customer communication within 15min"],
                "escalation_triggers": ["Downtime exceeds 1 hour", "Customer complaints increase"]
              },
              "executive_summary": {
                "headline": "URGENT: Critical Payment System Failure",
                "business_impact": "All payment processing stopped - $2000/minute revenue impact",
                "current_status": "Active incident response in progress",
                "resolution_timeline": "Target resolution within 30 minutes",
                "next_update": "Status update in 15 minutes",
                "executive_actions_needed": ["Consider public statement preparation", "Authorize emergency resources"]
              }
            }
            """;
        
        summaryModel.fixedResponse(mockResponse);

        String result = componentClient
                .forAgent()
                .inSession("test-session-urgent")
                .method(SummaryAgent::summarize)
                .invoke(new SummaryAgent.Request(
                    "Complete payment system failure, all customers affected",
                    "{\"service\":\"payment\",\"severity\":\"P1\",\"confidence\":9.5}",
                    "Business-critical outage with high revenue impact",
                    "Emergency response required"
                ));

        // Verify urgency assessment
        assertThat(result).contains("URGENT");
        assertThat(result).contains("HIGH");
        assertThat(result).contains("urgency_factors");
        
        // Verify escalated communication
        assertThat(result).contains("Immediate executive notification");
        assertThat(result).contains("emergency");
        assertThat(result).contains("revenue impact");
        
        // Verify executive focus
        assertThat(result).contains("public statement");
        assertThat(result).contains("emergency resources");
    }

    @Test
    @DisplayName("Should generate appropriate customer communications")
    public void generateCustomerCommunications() {
        String mockResponse = """
            {
              "customer_facing_summary": {
                "public_description": "We are experiencing technical difficulties with our payment processing system",
                "user_impact": "Some customers may experience delays or failures when making payments",
                "resolution_status": "Our team is actively working to resolve the issue",
                "customer_actions": "Please try your transaction again in a few minutes. Contact support if issues persist.",
                "support_information": "For urgent payment issues, contact support@company.com or call 1-800-SUPPORT"
              },
              "communication_tone": {
                "audience_type": "PUBLIC",
                "tone_guidance": "Professional, apologetic, reassuring",
                "key_messages": ["We acknowledge the issue", "We're working to fix it", "We'll keep you updated"],
                "avoid_language": ["Technical jargon", "Blame", "Uncertain timelines"]
              },
              "timeline": [
                {"time": "14:30 UTC", "event": "Issue first detected", "impact": "Payment processing affected"},
                {"time": "14:35 UTC", "event": "Incident response activated", "impact": "Team engaged"},
                {"time": "14:45 UTC", "event": "Root cause identified", "impact": "Solution path clear"},
                {"time": "15:00 UTC", "event": "Fix implementation started", "impact": "Resolution in progress"}
              ]
            }
            """;
        
        summaryModel.fixedResponse(mockResponse);

        String result = componentClient
                .forAgent()
                .inSession("test-session-customer")
                .method(SummaryAgent::summarize)
                .invoke(new SummaryAgent.Request(
                    "Payment processing issues affecting customers",
                    "{\"service\":\"payment\",\"severity\":\"P2\"}",
                    "Customer-facing impact identified",
                    "Communication strategy needed"
                ));

        // Verify customer-appropriate language
        assertThat(result).contains("technical difficulties");
        assertThat(result).contains("working to resolve");
        assertThat(result).doesNotContain("database");
        assertThat(result).doesNotContain("connection pool");
        
        // Verify customer actions
        assertThat(result).contains("try your transaction again");
        assertThat(result).contains("contact support");
        assertThat(result).contains("support@company.com");
        
        // Verify communication guidance
        assertThat(result).contains("tone_guidance");
        assertThat(result).contains("Professional, apologetic, reassuring");
        assertThat(result).contains("avoid_language");
        
        // Verify timeline for transparency
        assertThat(result).contains("timeline");
        assertThat(result).containsPattern("[0-9]{2}:[0-9]{2} UTC");
    }

    @Test
    @DisplayName("Should provide post-incident learning insights")
    public void providePostIncidentLearning() {
        String mockResponse = """
            {
              "post_incident_preview": {
                "lessons_learned": [
                  "Connection management code needs mandatory peer review",
                  "Database connection monitoring was insufficient",
                  "Rollback procedures took longer than expected"
                ],
                "prevention_measures": [
                  "Implement automated connection leak detection",
                  "Add connection pool utilization alerts",
                  "Create faster rollback automation"
                ],
                "process_improvements": [
                  "Reduce incident detection time from 5min to 2min",
                  "Improve team communication during incidents",
                  "Update runbooks with new scenarios"
                ],
                "follow_up_actions": [
                  "Schedule post-incident review meeting within 48 hours",
                  "Update monitoring dashboard with new metrics",
                  "Create connection management coding guidelines"
                ]
              },
              "learning_priorities": {
                "technical_debt": ["Connection management patterns", "Monitoring gap closure"],
                "process_debt": ["Incident response speed", "Communication protocols"],
                "organizational_debt": ["Team training on new tools", "Cross-team coordination"]
              }
            }
            """;
        
        summaryModel.fixedResponse(mockResponse);

        String result = componentClient
                .forAgent()
                .inSession("test-session-learning")
                .method(SummaryAgent::summarize)
                .invoke(new SummaryAgent.Request(
                    "Incident resolved but revealed process gaps",
                    "{\"service\":\"payment\",\"severity\":\"P2\"}",
                    "Multiple improvement opportunities identified",
                    "Process and technical improvements needed"
                ));

        // Verify learning capture
        assertThat(result).contains("lessons_learned");
        assertThat(result).contains("prevention_measures");
        assertThat(result).contains("process_improvements");
        
        // Verify actionable insights
        assertThat(result).contains("follow_up_actions");
        assertThat(result).contains("post-incident review");
        assertThat(result).contains("update monitoring");
        
        // Verify debt categorization
        assertThat(result).contains("learning_priorities");
        assertThat(result).contains("technical_debt");
        assertThat(result).contains("process_debt");
        assertThat(result).contains("organizational_debt");
        
        // Verify specific improvements
        assertThat(result).contains("connection");
        assertThat(result).contains("monitoring");
        assertThat(result).contains("rollback");
    }
}
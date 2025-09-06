package com.example.sample.individual;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.sample.application.RemediationAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RemediationAgent Individual Tests")
public class RemediationAgentTest extends TestKitSupport {

    private final TestModelProvider remediationModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
                .withModelProvider(RemediationAgent.class, remediationModel);
    }

    @Test
    @DisplayName("Should create comprehensive staged remediation plan")
    public void createStagedRemediationPlan() {
        String mockResponse = """
            {
              "risk_assessment": {
                "high_risk_factors": ["Production database restart", "Revenue-impacting rollback"],
                "medium_risk_factors": ["Service restart during business hours"],
                "mitigation_strategies": ["Staged rollout", "Immediate rollback capability"],
                "blast_radius": "Payment processing services only",
                "rollback_complexity": "LOW"
              },
              "staged_plan": {
                "immediate_actions": [
                  {
                    "step": 1,
                    "action": "Rollback deployment v2.1.3 to v2.1.2",
                    "purpose": "Eliminate connection leak immediately",
                    "risk_level": "MEDIUM",
                    "estimated_time": "5min",
                    "owner": "DevOps team",
                    "success_criteria": "Error rate drops below 1%",
                    "rollback_procedure": "Re-deploy v2.1.3 if needed",
                    "dependencies": ["Deployment pipeline ready"]
                  }
                ],
                "short_term_actions": [
                  {
                    "step": 2,
                    "action": "Scale database connection pool",
                    "purpose": "Provide buffer during investigation",
                    "risk_level": "LOW",
                    "estimated_time": "2min",
                    "owner": "DBA team",
                    "success_criteria": "Connection pool utilization < 80%",
                    "rollback_procedure": "Revert pool size configuration",
                    "dependencies": ["Database access confirmed"]
                  }
                ],
                "long_term_actions": []
              },
              "validation_strategy": {
                "health_checks": ["Payment API health endpoint", "Database connection count"],
                "key_metrics": ["Error rate", "Response time", "Connection pool utilization"],
                "automated_validations": ["Smoke tests", "Transaction verification"],
                "manual_verifications": ["Customer impact assessment", "SLA compliance check"]
              }
            }
            """;
        
        remediationModel.fixedResponse(mockResponse);

        String result = componentClient
                .forAgent()
                .inSession("test-session-remediation")
                .method(RemediationAgent::remediate)
                .invoke(new RemediationAgent.Request(
                    "Payment service down", 
                    "{\"service\":\"payment\",\"severity\":\"P1\"}", 
                    "{\"logs\":\"connection errors\",\"metrics\":\"pool exhausted\"}", 
                    "Root cause: connection leak in v2.1.3"
                ));

        // Verify JSON structure
        assertThat(result).contains("risk_assessment");
        assertThat(result).contains("staged_plan");
        assertThat(result).contains("validation_strategy");
        
        // Verify risk assessment
        assertThat(result).contains("high_risk_factors");
        assertThat(result).contains("rollback_complexity");
        
        // Verify staged planning
        assertThat(result).contains("immediate_actions");
        assertThat(result).contains("short_term_actions");
        assertThat(result).contains("estimated_time");
        assertThat(result).contains("success_criteria");
        
        // Verify validation strategy
        assertThat(result).contains("health_checks");
        assertThat(result).contains("key_metrics");
    }

    @Test
    @DisplayName("Should assess and mitigate high-risk remediation actions")
    public void assessHighRiskRemediation() {
        String mockResponse = """
            {
              "risk_assessment": {
                "high_risk_factors": ["Database schema change required", "Production data migration"],
                "medium_risk_factors": ["Service restart required"],
                "mitigation_strategies": ["Backup before changes", "Canary deployment", "Feature flags"],
                "blast_radius": "All payment services and customer data",
                "rollback_complexity": "HIGH"
              },
              "staged_plan": {
                "immediate_actions": [
                  {
                    "step": 1,
                    "action": "Create full database backup",
                    "purpose": "Ensure data safety before schema changes",
                    "risk_level": "LOW",
                    "estimated_time": "15min",
                    "owner": "DBA team",
                    "success_criteria": "Backup verification successful",
                    "rollback_procedure": "N/A - backup operation",
                    "dependencies": ["Maintenance window approval", "DBA availability"]
                  }
                ]
              },
              "contingency_planning": {
                "failure_scenarios": ["Rollback fails", "Data corruption", "Extended downtime"],
                "alternative_approaches": ["Hot standby activation", "Read-only mode"],
                "emergency_contacts": ["DBA on-call", "Infrastructure lead", "Product manager"]
              }
            }
            """;
        
        remediationModel.fixedResponse(mockResponse);

        String result = componentClient
                .forAgent()
                .inSession("test-session-highrisk")
                .method(RemediationAgent::remediate)
                .invoke(new RemediationAgent.Request(
                    "Database corruption in payment records", 
                    "{\"service\":\"payment\",\"severity\":\"P1\"}", 
                    "{\"logs\":\"schema errors\",\"metrics\":\"data integrity alerts\"}", 
                    "Root cause: corrupted schema migration"
                ));

        // Verify high-risk identification
        assertThat(result).contains("HIGH");
        assertThat(result).contains("high_risk_factors");
        assertThat(result).contains("Database");
        
        // Verify mitigation strategies
        assertThat(result).contains("mitigation_strategies");
        assertThat(result).contains("Backup");
        assertThat(result).contains("Feature flags");
        
        // Verify contingency planning
        assertThat(result).contains("contingency_planning");
        assertThat(result).contains("failure_scenarios");
        assertThat(result).contains("alternative_approaches");
        assertThat(result).contains("emergency_contacts");
    }

    @Test
    @DisplayName("Should plan resource allocation and coordination")
    public void planResourceAllocationAndCoordination() {
        String mockResponse = """
            {
              "staged_plan": {
                "immediate_actions": [
                  {
                    "step": 1,
                    "action": "Coordinate with multiple teams for rollback",
                    "owner": "Incident commander + DevOps + DBA + Product",
                    "estimated_time": "10min",
                    "dependencies": ["Team availability confirmed", "Rollback plan reviewed"]
                  }
                ]
              },
              "communication_plan": {
                "stakeholder_notifications": ["Executive team within 5min", "Customer support within 10min"],
                "status_updates": "Every 15 minutes to #incident-response channel",
                "escalation_triggers": ["Rollback fails", "Downtime exceeds 30min", "Customer complaints spike"]
              },
              "resource_requirements": {
                "personnel": ["2 DevOps engineers", "1 DBA", "1 Product manager", "1 Support lead"],
                "tools_access": ["Deployment pipeline", "Database admin tools", "Monitoring dashboards"],
                "time_commitment": "2-4 hours full attention required"
              }
            }
            """;
        
        remediationModel.fixedResponse(mockResponse);

        String result = componentClient
                .forAgent()
                .inSession("test-session-coordination")
                .method(RemediationAgent::remediate)
                .invoke(new RemediationAgent.Request(
                    "Multi-service cascade failure requiring coordinated response", 
                    "{\"service\":\"multiple\",\"severity\":\"P1\"}", 
                    "{\"logs\":\"cascade errors\",\"metrics\":\"multiple service alerts\"}", 
                    "Complex incident requiring team coordination"
                ));

        // Verify resource planning
        assertThat(result).contains("personnel");
        assertThat(result).contains("DevOps");
        assertThat(result).contains("DBA");
        
        // Verify communication planning
        assertThat(result).contains("communication_plan");
        assertThat(result).contains("stakeholder_notifications");
        assertThat(result).contains("status_updates");
        assertThat(result).contains("escalation_triggers");
        
        // Verify coordination aspects
        assertThat(result).contains("dependencies");
        assertThat(result).contains("Team availability");
        assertThat(result).contains("time_commitment");
    }

    @Test
    @DisplayName("Should provide detailed rollback procedures")
    public void provideDetailedRollbackProcedures() {
        String mockResponse = """
            {
              "staged_plan": {
                "immediate_actions": [
                  {
                    "step": 1,
                    "action": "Deploy rollback to v1.2.0",
                    "rollback_procedure": "If rollback fails: 1) Stop traffic to service, 2) Manual restore from backup, 3) Restart service cluster, 4) Gradually restore traffic",
                    "success_criteria": "Health checks pass and error rate < 1%",
                    "failure_threshold": "If error rate > 5% after 5min, escalate to emergency procedure"
                  }
                ]
              },
              "rollback_safety": {
                "pre_rollback_checklist": ["Backup current state", "Notify stakeholders", "Prepare monitoring"],
                "rollback_validation": ["Automated health checks", "Manual smoke tests", "Customer impact assessment"],
                "emergency_stops": ["Circuit breaker activation", "Traffic diversion", "Service isolation"]
              }
            }
            """;
        
        remediationModel.fixedResponse(mockResponse);

        String result = componentClient
                .forAgent()
                .inSession("test-session-rollback")
                .method(RemediationAgent::remediate)
                .invoke(new RemediationAgent.Request(
                    "Failed deployment needs rollback", 
                    "{\"service\":\"api\",\"severity\":\"P2\"}", 
                    "{\"logs\":\"deployment errors\",\"metrics\":\"increased latency\"}", 
                    "Bad deployment in production"
                ));

        // Verify rollback procedures
        assertThat(result).contains("rollback_procedure");
        assertThat(result).contains("success_criteria");
        assertThat(result).contains("failure_threshold");
        
        // Verify safety measures
        assertThat(result).contains("rollback_safety");
        assertThat(result).contains("pre_rollback_checklist");
        assertThat(result).contains("emergency_stops");
        
        // Verify detailed steps
        assertThat(result).contains("Health checks");
        assertThat(result).contains("error rate");
        assertThat(result).contains("Circuit breaker");
    }
}
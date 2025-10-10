package com.example.sample.individual;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.sample.application.TriageAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TriageAgent Individual Tests")
public class TriageAgentTest extends TestKitSupport {

    private final TestModelProvider triageModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
                .withModelProvider(TriageAgent.class, triageModel);
    }

    @Test
    @DisplayName("Should perform systematic root cause analysis")
    public void performRootCauseAnalysis() {
        String mockResponse = """
            SYSTEMATIC DIAGNOSIS RESULTS:
            
            1. INITIAL ASSESSMENT:
            - Business Impact: HIGH - Payment processing completely down
            - User Impact: 100% of payment transactions failing
            - Affected Systems: payment-service, payment-gateway
            
            2. ROOT CAUSE ANALYSIS (5 Whys):
            Why 1: Payment service returning 503 errors
            Why 2: Database connections being refused
            Why 3: Connection pool exhausted
            Why 4: Connection leak in recent deployment
            Why 5: Missing connection cleanup in new payment method
            
            3. HYPOTHESIS:
            Primary: Connection leak in v2.1.3 deployment causing pool exhaustion
            Confidence: 8.5/10
            
            4. IMMEDIATE ACTIONS:
            1) Rollback to v2.1.2 immediately
            2) Scale database connection pool as temporary measure
            3) Monitor connection metrics closely
            """;
        
        triageModel.fixedResponse(mockResponse);

        String enrichedContext = """
            INCIDENT CONTEXT FOR TRIAGE
            ===========================
            Original Incident: Payment service is completely down since 14:30 UTC. Users are unable to complete transactions.
            
            Classification Results: {"service":"payment-service","severity":"P1","domain":"payments","rationale":"complete outage"}
            
            Evidence Analysis: {"logs":"ERROR: Connection pool exhausted", "metrics":"db_connections: 100/100"}
            
            Timestamp: 2024-01-15T18:45:00
            """;

        String result = componentClient
                .forAgent()
                .inSession("test-session-triage")
                .method(TriageAgent::triage)
                .invoke(new TriageAgent.Request(enrichedContext));

        // Verify systematic analysis structure
        assertThat(result.toLowerCase()).contains("hypothesis");
        assertThat(result.toLowerCase()).contains("root cause");
        assertThat(result.toLowerCase()).contains("actions");
        assertThat(result.toLowerCase()).contains("confidence");
        
        // Verify 5 Whys methodology
        assertThat(result).contains("Why");
        assertThat(result).containsPattern("Why [1-5]:");
        
        // Verify confidence scoring
        assertThat(result).containsPattern("[0-9]\\.[0-9]/10");
    }

    @Test
    @DisplayName("Should prioritize immediate mitigation actions")
    public void prioritizeImmediateActions() {
        String mockResponse = """
            TRIAGE ANALYSIS COMPLETE:
            
            IMMEDIATE MITIGATION (Stop the bleeding):
            1) URGENT: Rollback deployment v2.1.3 to v2.1.2 (ETA: 5 minutes)
            2) URGENT: Scale database connection pool from 100 to 200 (ETA: 2 minutes)
            3) MEDIUM: Enable circuit breaker for payment gateway (ETA: 3 minutes)
            
            DIAGNOSTIC ACTIONS (Validate hypothesis):
            1) Monitor connection pool metrics during rollback
            2) Analyze connection leak patterns in v2.1.3 code
            3) Review deployment timing vs incident start time
            
            CONFIDENCE ASSESSMENT:
            - Root cause confidence: 8.5/10
            - Mitigation success probability: 9.2/10
            """;
        
        triageModel.fixedResponse(mockResponse);

        String context = "Payment service outage context with evidence and classification";

        String result = componentClient
                .forAgent()
                .inSession("test-session-mitigation")
                .method(TriageAgent::triage)
                .invoke(new TriageAgent.Request(context));

        // Verify action prioritization
        assertThat(result).contains("IMMEDIATE");
        assertThat(result).contains("URGENT");
        assertThat(result).contains("DIAGNOSTIC");
        
        // Verify time estimates
        assertThat(result).containsPattern("ETA: [0-9]+ minutes");
        
        // Verify confidence assessment
        assertThat(result).contains("confidence");
        assertThat(result).containsPattern("[0-9]\\.[0-9]/10");
    }

    @Test
    @DisplayName("Should handle complex distributed system failures")
    public void handleComplexDistributedFailures() {
        String mockResponse = """
            COMPLEX SYSTEM FAILURE ANALYSIS:
            
            CASCADING FAILURE PATTERN DETECTED:
            1) Initial trigger: Auth service high latency
            2) Cascade effect: Payment service timeouts
            3) Propagation: User service session failures
            4) Amplification: Load balancer health checks failing
            
            DEPENDENCY ANALYSIS:
            - Critical path: Auth -> Payment -> User -> Frontend
            - Failure correlation: 95% correlation between auth latency and payment errors
            - Blast radius: 3 services, ~50,000 users affected
            
            RECOMMENDED APPROACH:
            1) Address root cause: Auth service performance
            2) Implement circuit breakers to prevent cascade
            3) Graceful degradation for non-critical features
            """;
        
        triageModel.fixedResponse(mockResponse);

        String complexContext = """
            Multi-service failure: Auth service showing high latency, payment service timing out,
            user service session failures. Multiple services affected with cascading failures.
            """;

        String result = componentClient
                .forAgent()
                .inSession("test-session-complex")
                .method(TriageAgent::triage)
                .invoke(new TriageAgent.Request(complexContext));

        System.out.println("handleComplexDistributedFailures result: " + result);

        // Verify cascade analysis
        assertThat(result).contains("CASCADING");
        assertThat(result).contains("cascade");
        
        // Verify dependency analysis
        assertThat(result.toLowerCase()).containsIgnoringWhitespaces("dependency");
    }

    @Test
    @DisplayName("Should assess business impact and urgency")
    public void assessBusinessImpactAndUrgency() {
        String mockResponse = """
            BUSINESS IMPACT ASSESSMENT:
            
            FINANCIAL IMPACT:
            - Revenue impact: $10,000/hour (payment processing down)
            - SLA violation: Tier 1 customers affected
            - Reputation risk: HIGH (social media mentions increasing)
            
            OPERATIONAL URGENCY:
            - Escalation level: Executive notification required
            - Response time: Immediate (< 5 minutes)
            - Communication plan: Customer notification in 15 minutes
            
            TECHNICAL PRIORITY:
            - System criticality: Core business function
            - Recovery complexity: MEDIUM (rollback available)
            - Resource requirements: 2 engineers + 1 DBA
            """;
        
        triageModel.fixedResponse(mockResponse);

        String businessContext = "Payment processing completely down, affecting all customers";

        String result = componentClient
                .forAgent()
                .inSession("test-session-business")
                .method(TriageAgent::triage)
                .invoke(new TriageAgent.Request(businessContext));

        System.out.println("assessBusinessImpactAndUrgency result: " + result);

        // Verify business impact assessment
        assertThat(result.toLowerCase()).containsIgnoringWhitespaces("revenue");
    }
}
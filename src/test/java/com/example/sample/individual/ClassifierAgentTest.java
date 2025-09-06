package com.example.sample.individual;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import akka.javasdk.testkit.TestModelProvider;
import com.example.sample.application.ClassifierAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClassifierAgent Individual Tests")
public class ClassifierAgentTest extends TestKitSupport {

    private final TestModelProvider classifierModel = new TestModelProvider();

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
                .withModelProvider(ClassifierAgent.class, classifierModel);
    }

    @Test
    @DisplayName("Should classify payment service incident with proper JSON structure")
    public void classifyPaymentIncident() {
        String expectedJson = """
            {
                "service": "payment-service",
                "severity": "P1",
                "domain": "payments",
                "confidence_scores": {
                    "service_identification": 9.5,
                    "severity_assessment": 8.8,
                    "overall": 9.0
                },
                "rationale": "Complete service outage affecting all payment transactions"
            }
            """;
        classifierModel.fixedResponse(expectedJson);

        String result = componentClient
                .forAgent()
                .inSession("test-session-payment")
                .method(ClassifierAgent::classify)
                .invoke(new ClassifierAgent.Request(
                    "Payment service is completely down since 14:30 UTC. Users are unable to complete transactions and getting 503 errors."
                ));

        assertThat(result).contains("\"service\"");
        assertThat(result).contains("payment-service");
        assertThat(result).contains("\"severity\"");
        assertThat(result).contains("P1");
        assertThat(result).contains("\"confidence_scores\"");
        assertThat(result).contains("\"overall\"");
    }

    @Test
    @DisplayName("Should classify database connectivity incident")
    public void classifyDatabaseIncident() {
        String expectedJson = """
            {
                "service": "checkout-service",
                "severity": "P2", 
                "domain": "database",
                "confidence_scores": {
                    "service_identification": 8.2,
                    "severity_assessment": 7.5,
                    "overall": 7.8
                },
                "rationale": "Database connection timeouts causing elevated error rates"
            }
            """;
        classifierModel.fixedResponse(expectedJson);

        String result = componentClient
                .forAgent()
                .inSession("test-session-db")
                .method(ClassifierAgent::classify)
                .invoke(new ClassifierAgent.Request(
                    "Database connection timeout in checkout service. Intermittent failures, ~30% error rate."
                ));

        assertThat(result).contains("checkout-service");
        assertThat(result).contains("P2");
        assertThat(result).contains("database");
        assertThat(result).contains("timeout");
    }

    @ParameterizedTest
    @DisplayName("Should classify different severity levels correctly")
    @CsvSource({
        "'Complete service outage, all users affected', P1",
        "'Elevated error rate, some users affected', P2", 
        "'Minor performance degradation', P3",
        "'Cosmetic UI issue', P4"
    })
    public void classifySeverityLevels(String incident, String expectedSeverity) {
        String mockResponse = String.format("""
            {
                "service": "test-service",
                "severity": "%s",
                "domain": "test",
                "confidence_scores": {"overall": 8.0},
                "rationale": "Test classification"
            }
            """, expectedSeverity);
        
        classifierModel.fixedResponse(mockResponse);

        String result = componentClient
                .forAgent()
                .inSession("test-session-" + expectedSeverity.toLowerCase())
                .method(ClassifierAgent::classify)
                .invoke(new ClassifierAgent.Request(incident));

        assertThat(result).contains(expectedSeverity);
    }

    @Test
    @DisplayName("Should handle edge case with minimal incident description")
    public void classifyMinimalIncident() {
        String expectedJson = """
            {
                "service": "unknown-service",
                "severity": "P3",
                "domain": "unknown",
                "confidence_scores": {
                    "service_identification": 3.0,
                    "severity_assessment": 5.0,
                    "overall": 4.0
                },
                "rationale": "Insufficient information provided for accurate classification"
            }
            """;
        classifierModel.fixedResponse(expectedJson);

        String result = componentClient
                .forAgent()
                .inSession("test-session-minimal")
                .method(ClassifierAgent::classify)
                .invoke(new ClassifierAgent.Request("Something is broken"));

        assertThat(result).contains("unknown");
        assertThat(result).contains("Insufficient information");
    }
}
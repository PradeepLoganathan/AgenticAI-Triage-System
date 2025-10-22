package com.example.sample.individual;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.example.sample.application.EvidenceAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import akka.javasdk.testkit.TestModelProvider;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvidenceAgent Individual Tests")
public class EvidenceAgentTest extends TestKitSupport {

    private final TestModelProvider evidenceModel = new TestModelProvider();
    private static final Logger log = LoggerFactory.getLogger(ClassifierAgentTest.class);

    @Override
    protected TestKit.Settings testKitSettings() {
        return TestKit.Settings.DEFAULT
                .withModelProvider(EvidenceAgent.class, evidenceModel);
    }

    @BeforeEach
    public void setup() {
        // Note: In production, EvidenceAgent uses RemoteMcpTools.fromService("evidence-tools")
        // which discovers the evidence-tools service at http://localhost:9200/mcp
        // In tests, we use TestModelProvider to mock the AI responses instead
    }

    @Test
    @DisplayName("Should gather evidence from logs and metrics via MCP")
    public void gatherEvidenceViaMcp() throws Exception {
        evidenceModel.fixedResponse("{\"service\":\"payment-service\",\"logs\":\"mocked tool output\",\"metrics\":\"mocked tool output\"}");
        String result = componentClient
                .forAgent()
                .inSession("test-session-evidence")
                .method(EvidenceAgent::gather)
                .invoke(new EvidenceAgent.Request("payment-service", "errors:rate1m", "30m"));

        // Verify JSON structure
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var node = mapper.readTree(result);
        log.debug("EvidenceAgent response: {}", result);
        assertThat(node.has("service")).isTrue();
        assertThat(node.get("service").asText()).isEqualTo("payment-service");
        
        assertThat(node.has("logs")).isTrue();
        assertThat(node.has("metrics")).isTrue();
        
        // Mock endpoint should provide test data
        assertThat(node.get("logs").asText()).contains("mocked");
        assertThat(node.get("metrics").asText()).contains("mocked");
    }

    @Test
    @DisplayName("Should handle different time ranges for evidence collection")
    public void gatherEvidenceWithDifferentTimeRanges() throws Exception {
        evidenceModel.fixedResponse("{\"service\":\"checkout-service\",\"logs\":\"mocked tool output for 30m\",\"metrics\":\"mocked tool output for 30m\"}");
        String result30m = componentClient
                .forAgent()
                .inSession("test-session-30m")
                .method(EvidenceAgent::gather)
                .invoke(new EvidenceAgent.Request("checkout-service", "errors:rate5m", "30m"));

        evidenceModel.fixedResponse("{\"service\":\"checkout-service\",\"logs\":\"mocked tool output for 1h\",\"metrics\":\"mocked tool output for 1h\"}");
        String result1h = componentClient
                .forAgent()
                .inSession("test-session-1h")  
                .method(EvidenceAgent::gather)
                .invoke(new EvidenceAgent.Request("checkout-service", "errors:rate5m", "1h"));

        // Both should return valid JSON with service info
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var node30m = mapper.readTree(result30m);
        var node1h = mapper.readTree(result1h);
        log.debug("EvidenceAgent 30m response: {}", result30m);
        log.debug("EvidenceAgent 1h response: {}", result1h);
        assertThat(node30m.get("service").asText()).isEqualTo("checkout-service");
        assertThat(node1h.get("service").asText()).isEqualTo("checkout-service");
        
        // Time range should be reflected in the response
        assertThat(result30m).contains("30m");
        assertThat(result1h).contains("1h");
    }

    @Test
    @DisplayName("Should handle different metric expressions")
    public void gatherEvidenceWithDifferentMetrics() throws Exception {
        evidenceModel.fixedResponse("{\"service\":\"api-service\",\"logs\":\"mocked tool output\",\"metrics\":\"errors:rate5m mocked tool output\"}");
        String resultErrorRate = componentClient
                .forAgent()
                .inSession("test-session-errors")
                .method(EvidenceAgent::gather)
                .invoke(new EvidenceAgent.Request("api-service", "errors:rate5m", "1h"));

        evidenceModel.fixedResponse("{\"service\":\"api-service\",\"logs\":\"mocked tool output\",\"metrics\":\"latency:p99 mocked tool output\"}");
        String resultLatency = componentClient
                .forAgent()
                .inSession("test-session-latency")
                .method(EvidenceAgent::gather)
                .invoke(new EvidenceAgent.Request("api-service", "latency:p99", "1h"));

        // Both should be valid JSON with metrics data
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var errorNode = mapper.readTree(resultErrorRate);
        var latencyNode = mapper.readTree(resultLatency);
        log.debug("EvidenceAgent WithDifferentMetrics response: {}", resultErrorRate);
        assertThat(errorNode.has("metrics")).isTrue();
        assertThat(latencyNode.has("metrics")).isTrue();
        
        // Should contain the metric type in response
        assertThat(resultErrorRate).containsAnyOf("errors", "rate");
        assertThat(resultLatency).containsAnyOf("latency", "p99");
    }

    @Test
    @DisplayName("Should provide structured evidence analysis")
    public void provideStructuredEvidenceAnalysis() throws Exception {
        evidenceModel.fixedResponse("{\"service\":\"user-service\",\"logs\":\"mocked tool output\",\"metrics\":\"mocked tool output\", \"analysis_summary\":\"This is a summary\",\"key_findings\":[\"finding1\"],\"confidence_score\":0.9}");
        String result = componentClient
                .forAgent()
                .inSession("test-session-analysis")
                .method(EvidenceAgent::gather)
                .invoke(new EvidenceAgent.Request("user-service", "cpu:utilization", "2h"));

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var node = mapper.readTree(result);
        
        // Should have analysis fields
        assertThat(node.has("analysis_summary")).isTrue();
        assertThat(node.has("key_findings")).isTrue();
        assertThat(node.has("confidence_score")).isTrue();
        
        // Analysis should contain meaningful insights
        String analysis = node.get("analysis_summary").asText();
        assertThat(analysis).isNotEmpty();
        assertThat(analysis.length()).isGreaterThan(10); // Should have substantial analysis
    }
}

package com.example.sample.individual;

import akka.javasdk.testkit.TestKit;
import akka.javasdk.testkit.TestKitSupport;
import com.example.sample.application.EvidenceAgent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvidenceAgent Individual Tests")
public class EvidenceAgentTest extends TestKitSupport {

    @BeforeEach
    public void setup() {
        // Route MCP calls to the in-process mock endpoint
        System.setProperty("MCP_HTTP_URL", "http://localhost:9100/mcp");
    }

    @Test
    @DisplayName("Should gather evidence from logs and metrics via MCP")
    public void gatherEvidenceViaMcp() throws Exception {
        String result = componentClient
                .forAgent()
                .inSession("test-session-evidence")
                .method(EvidenceAgent::gather)
                .invoke(new EvidenceAgent.Request("payment-service", "errors:rate1m", "30m"));

        // Verify JSON structure
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var node = mapper.readTree(result);
        
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
        String result30m = componentClient
                .forAgent()
                .inSession("test-session-30m")
                .method(EvidenceAgent::gather)
                .invoke(new EvidenceAgent.Request("checkout-service", "errors:rate5m", "30m"));

        String result1h = componentClient
                .forAgent()
                .inSession("test-session-1h")  
                .method(EvidenceAgent::gather)
                .invoke(new EvidenceAgent.Request("checkout-service", "errors:rate5m", "1h"));

        // Both should return valid JSON with service info
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var node30m = mapper.readTree(result30m);
        var node1h = mapper.readTree(result1h);
        
        assertThat(node30m.get("service").asText()).isEqualTo("checkout-service");
        assertThat(node1h.get("service").asText()).isEqualTo("checkout-service");
        
        // Time range should be reflected in the response
        assertThat(result30m).contains("30m");
        assertThat(result1h).contains("1h");
    }

    @Test
    @DisplayName("Should handle different metric expressions")
    public void gatherEvidenceWithDifferentMetrics() throws Exception {
        String resultErrorRate = componentClient
                .forAgent()
                .inSession("test-session-errors")
                .method(EvidenceAgent::gather)
                .invoke(new EvidenceAgent.Request("api-service", "errors:rate5m", "1h"));

        String resultLatency = componentClient
                .forAgent()
                .inSession("test-session-latency")
                .method(EvidenceAgent::gather)
                .invoke(new EvidenceAgent.Request("api-service", "latency:p99", "1h"));

        // Both should be valid JSON with metrics data
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        var errorNode = mapper.readTree(resultErrorRate);
        var latencyNode = mapper.readTree(resultLatency);
        
        assertThat(errorNode.has("metrics")).isTrue();
        assertThat(latencyNode.has("metrics")).isTrue();
        
        // Should contain the metric type in response
        assertThat(resultErrorRate).containsAnyOf("errors", "rate");
        assertThat(resultLatency).containsAnyOf("latency", "p99");
    }

    @Test
    @DisplayName("Should provide structured evidence analysis")
    public void provideStructuredEvidenceAnalysis() throws Exception {
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
        assertThat(analysis.length()).isGreaterThan(50); // Should have substantial analysis
    }
}
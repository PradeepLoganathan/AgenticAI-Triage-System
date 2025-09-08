package com.example.sample;

import com.example.sample.application.EvidenceAgent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("EvidenceAgent File-based Log Analysis Tests")
public class EvidenceAgentFileTest {

    @Test
    @DisplayName("Should fetch and analyze payment service logs")
    public void testPaymentServiceLogs() {
        EvidenceAgent agent = new EvidenceAgent();
        
        String result = agent.fetchLogs("payment-service", 10);
        
        System.out.println("=== PAYMENT SERVICE LOGS ===");
        System.out.println(result);
        System.out.println();
        
        // Verify the log analysis
        assertThat(result).contains("payment-service");
        assertThat(result).contains("Error count:");
        assertThat(result).contains("Error patterns found:");
        assertThat(result).contains("Recent 10 lines");
        
        // Should detect payment gateway timeouts and database issues
        assertThat(result).containsAnyOf("timeout", "gateway", "database", "connection");
    }
    
    @Test
    @DisplayName("Should fetch and analyze user service logs")
    public void testUserServiceLogs() {
        EvidenceAgent agent = new EvidenceAgent();
        
        String result = agent.fetchLogs("user-service", 15);
        
        System.out.println("=== USER SERVICE LOGS ===");
        System.out.println(result);
        System.out.println();
        
        assertThat(result).contains("user-service");
        assertThat(result).contains("Recent 15 lines");
        
        // Should detect authentication failures and connection pool issues
        assertThat(result).containsAnyOf("login", "authentication", "pool", "connection");
    }
    
    @Test
    @DisplayName("Should handle non-existent service gracefully")
    public void testNonExistentService() {
        EvidenceAgent agent = new EvidenceAgent();
        
        String result = agent.fetchLogs("non-existent-service", 5);
        
        System.out.println("=== NON-EXISTENT SERVICE TEST ===");
        System.out.println(result);
        System.out.println();
        
        assertThat(result).contains("No log file found for service: non-existent-service");
    }
    
    @Test
    @DisplayName("Should generate synthetic metrics based on query type")
    public void testSyntheticMetrics() {
        EvidenceAgent agent = new EvidenceAgent();
        
        // Test error metrics
        String errorMetrics = agent.queryMetrics("errors:rate5m", "1h");
        System.out.println("=== ERROR METRICS ===");
        System.out.println(errorMetrics);
        System.out.println();
        
        assertThat(errorMetrics).contains("Error Rate");
        assertThat(errorMetrics).contains("Total Errors");
        assertThat(errorMetrics).contains("Spike Detected");
        
        // Test latency metrics
        String latencyMetrics = agent.queryMetrics("response_time:avg", "30m");
        System.out.println("=== LATENCY METRICS ===");
        System.out.println(latencyMetrics);
        System.out.println();
        
        assertThat(latencyMetrics).contains("Latency P95");
        assertThat(latencyMetrics).contains("Average Latency");
        
        // Test resource metrics
        String resourceMetrics = agent.queryMetrics("cpu:usage", "15m");
        System.out.println("=== RESOURCE METRICS ===");
        System.out.println(resourceMetrics);
        System.out.println();
        
        assertThat(resourceMetrics).contains("CPU Usage");
        assertThat(resourceMetrics).contains("Memory");
        
        // Test throughput metrics
        String throughputMetrics = agent.queryMetrics("throughput:rate", "1h");
        System.out.println("=== THROUGHPUT METRICS ===");
        System.out.println(throughputMetrics);
        System.out.println();
        
        assertThat(throughputMetrics).contains("Request Rate");
        assertThat(throughputMetrics).contains("Success Rate");
    }
    
    @Test
    @DisplayName("Should analyze order service logs and detect patterns")
    public void testOrderServiceLogAnalysis() {
        EvidenceAgent agent = new EvidenceAgent();
        
        String result = agent.fetchLogs("order-service", 20);
        
        System.out.println("=== ORDER SERVICE LOG ANALYSIS ===");
        System.out.println(result);
        System.out.println();
        
        assertThat(result).contains("order-service");
        
        // Should detect inventory service timeouts and order failures
        assertThat(result).containsAnyOf("inventory", "timeout", "order", "failed");
    }
}
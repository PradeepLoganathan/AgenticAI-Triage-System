package com.example.sample;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import akka.javasdk.testkit.TestKitSupport;
import com.example.sample.application.TriageAgent;

public class TriageAgentMcpTest extends TestKitSupport {

    @BeforeEach
    public void setup() {
        // Route agent MCP calls to the in-process mock endpoint
        System.setProperty("MCP_HTTP_URL", "http://localhost:9100/mcp");
    }

    @Test
    public void mcpCall_returnsMockedResult() {
        String response = componentClient
            .forAgent()
            .inSession(java.util.UUID.randomUUID().toString())
            .method(TriageAgent::mcpCall)
            .invoke("fetch_logs", "{\"service\":\"checkout\",\"lines\":10}");

        assertTrue(response.contains("\"jsonrpc\":"));
        assertTrue(response.contains("\"result\":"));
        assertTrue(response.contains("mocked tool output"));
    }
}

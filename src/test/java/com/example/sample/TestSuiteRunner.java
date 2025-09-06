package com.example.sample;

import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

/**
 * Comprehensive test suite for the Agentic Workflow System
 * 
 * Usage:
 * - Run individual agent tests: mvn test -Dtest="*Agent*Test"
 * - Run workflow integration tests: mvn test -Dtest="WorkflowTest" 
 * - Run all tests: mvn test -Dtest="TestSuiteRunner"
 */
@Suite
@SuiteDisplayName("Agentic Workflow Complete Test Suite")
@SelectPackages({
    "com.example.sample.individual", // Individual agent tests
    "com.example.sample"            // Integration and workflow tests
})
public class TestSuiteRunner {
    /*
     * Test Organization:
     * 
     * 1. INDIVIDUAL AGENT TESTS (Fast, Isolated):
     *    - ClassifierAgentTest: Classification accuracy and JSON structure
     *    - EvidenceAgentTest: Evidence collection via MCP integration  
     *    - TriageAgentTest: Systematic diagnosis and root cause analysis
     *    - RemediationAgentTest: Risk assessment and staged planning
     *    - SummaryAgentTest: Multi-audience communication generation
     * 
     * 2. INTEGRATION TESTS (Slower, End-to-End):
     *    - WorkflowTest: Complete triage workflow with all agents
     *    - AgentsTest: Legacy combined agent tests
     * 
     * 3. SPECIALIZED TESTS:
     *    - ClassifierAgentJsonTest: JSON parsing validation
     *    - TriageAgentMcpTest: MCP integration testing
     * 
     * Quick Commands:
     * - Test single agent: mvn test -Dtest="ClassifierAgentTest"
     * - Test workflow only: mvn test -Dtest="WorkflowTest"  
     * - Test with OpenAI: mvn test -DOPENAI_API_KEY=your_key
     * - Debug single test: mvn test -Dtest="TriageAgentTest#performRootCauseAnalysis" -X
     */
}
package com.pradeepl.triage.application.agents;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.MemoryProvider;
import akka.javasdk.agent.RemoteMcpTools;
import akka.javasdk.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(id = "knowledge-base-agent")
public class KnowledgeBaseAgent extends Agent {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseAgent.class);
    
    private static final String SYSTEM = """
        You are a knowledge base retrieval assistant with access to service runbooks via MCP resources.

        AVAILABLE RESOURCES:
        - You have access to service-specific runbooks via the URI pattern: kb://runbooks/{serviceName}
        - Available services include: payment-service, checkout-service, auth-service, api-gateway,
          order-service, user-service, database, and others

        TASK:
        1. Analyze the user's query to identify the relevant service(s)
        2. Access the appropriate runbook(s) using the kb://runbooks/{serviceName} resource
        3. Extract and summarize the relevant troubleshooting information
        4. Provide clear, actionable guidance based on the runbook content

        GUIDELINES:
        - If the query mentions a specific service, access that service's runbook directly
        - If multiple services might be relevant, access multiple runbooks
        - If no specific service is mentioned, start with common services (payment, auth, api-gateway)
        - Always cite which runbook you're referencing
        - Be concise but thorough in your summaries

        Always access the MCP resources before responding.
        """;

    public Effect<String> search(String query) {
        logger.info("🧠 KnowledgeBaseAgent.search() - invoking model with MCP resources for query: {}", query);
        logger.info("🔗 Using MCP resources from evidence-tools service");

        return effects()
                .model(
                        ModelProvider.openAi()
                                .withApiKey(System.getenv("OPENAI_API_KEY"))
                                .withModelName("gpt-4o-mini")
                                .withTemperature(0.1)
                                .withMaxTokens(2000)
                )
                .memory(MemoryProvider.limitedWindow())
                .mcpTools(
                        RemoteMcpTools.fromService("evidence-tools")
                        // No .withAllowedToolNames() - this gives access to ALL tools AND resources
                )
                .systemMessage(SYSTEM)
                .userMessage("Query: " + (query == null ? "" : query))
                .thenReply();
    }

}

# Akka SDK Native MCP Support - Research Findings

**Date:** 2025-10-22
**Akka Java SDK Version:** 3.5.4
**Branch:** mcp-updates

## Executive Summary

✅ **Akka Java SDK 3.5.4 HAS NATIVE MCP SUPPORT!**

The current custom MCP implementation in this project is **unnecessary**. Akka provides built-in, production-ready MCP integration with two distinct capabilities:

1. **Agent-side**: Agents can consume tools from remote MCP servers via `.mcpTools()`
2. **Server-side**: Expose function tools as MCP endpoints via `@McpEndpoint` annotations

## Key Findings

### 1. Native Agent MCP Integration

Akka agents support `.mcpTools()` in the effects API to connect to remote MCP servers:

```java
public Effect<String> query(String message) {
    return effects()
        .model(ModelProvider.openAi()...)
        .memory(MemoryProvider.limitedWindow())
        .mcpTools(
            RemoteMcpTools.fromService("evidence-service"),  // Other Akka services
            RemoteMcpTools.fromServer("http://localhost:7400/jsonrpc")  // External MCP
                .addClientHeader(Authorization.oauth2(apiToken))
                .withAllowedToolNames(Set.of("fetch_logs", "query_metrics"))
                .withTimeout(Duration.ofSeconds(30))
        )
        .systemMessage(SYSTEM)
        .userMessage(message)
        .thenReply();
}
```

**Features:**
- Connect to multiple MCP servers simultaneously
- Custom headers for authentication (OAuth, API keys)
- Tool name filtering (allowlist)
- Request/response interceptors
- Configurable timeouts (default: 20 seconds)
- Supports both Akka services and third-party MCP servers

### 2. Native MCP Endpoint Exposure

Expose local functions as MCP tools using annotations:

```java
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@McpEndpoint(serverName = "evidence-tools", serverVersion = "1.0.0")
public class EvidenceToolsEndpoint {

    @McpTool(
        name = "fetch_logs",
        description = "Fetch service logs for incident analysis"
    )
    public String fetchLogs(
        @Description("Service name to fetch logs from") String service,
        @Description("Number of log lines to fetch") int lines
    ) {
        // Implementation
        return logsJson;
    }

    @McpTool(name = "query_metrics")
    public String queryMetrics(
        @Description("Metrics expression") String expr,
        @Description("Time range") String range
    ) {
        // Implementation
        return metricsJson;
    }

    @McpResource(
        uri = "file:///runbooks/payment-service.md",
        name = "Payment Service Runbook",
        description = "Troubleshooting guide for payment service",
        mimeType = "text/markdown"
    )
    public String paymentRunbook() {
        return Files.readString(Paths.get("knowledge_base/payment-service-runbook.md"));
    }

    @McpPrompt(description = "Incident triage template")
    public String triagePrompt(
        @Description("Incident description") String incident
    ) {
        return "Analyze this incident: " + incident;
    }
}
```

**Features:**
- `@McpTool` - Expose functions as callable tools
- `@McpResource` - Expose data/documents (static URIs or dynamic templates)
- `@McpPrompt` - Provide prompt templates with parameters
- Automatic JSON-RPC 2.0 endpoint at `/mcp`
- Full JSON Schema generation from Java types
- Component injection via constructor (e.g., `ComponentClient`)

### 3. RemoteMcpTools API

The `akka.javasdk.agent.RemoteMcpTools` class provides:

```java
// Connect to external MCP server
RemoteMcpTools external = RemoteMcpTools.fromServer("https://mcp.example.com/sse")
    .addClientHeader(HttpHeader.create("Authorization", "Bearer " + token))
    .withTimeout(Duration.ofSeconds(30))
    .withAllowedToolNames("fetch_logs", "query_metrics")
    .withToolInterceptor((toolName, arguments) -> {
        logger.info("Calling tool: {}", toolName);
        return true; // allow
    });

// Connect to other Akka service with MCP endpoint
RemoteMcpTools internal = RemoteMcpTools.fromService("knowledge-base-service")
    .withAllowedToolNames(Set.of("search_kb"));
```

## Comparison: Current vs. Native Implementation

| Aspect | Current Custom Implementation | Akka Native MCP Support |
|--------|------------------------------|-------------------------|
| **JSON-RPC Construction** | Manual string concatenation | Automatic, type-safe |
| **Error Handling** | Returns `MCP_ERROR:` prefix | Proper JSON-RPC error objects |
| **Type Safety** | None (raw strings) | Full type inference from Java types |
| **Authentication** | None | Built-in header support (OAuth, API keys) |
| **Tool Filtering** | None | Allowlist, custom predicates |
| **Interceptors** | None | Request/response interceptors |
| **Timeout Config** | Hardcoded 15s | Configurable per server |
| **Protocol Support** | HTTP only | HTTP, SSE, stdio (via server) |
| **MCP Primitives** | Tools only | Tools, Resources, Prompts |
| **Agent Integration** | Manual McpClient instantiation | Declarative `.mcpTools()` in effects |
| **Server Exposure** | Custom endpoint with manual JSON | Annotations with auto-generation |
| **Maintenance** | Custom code to maintain | Maintained by Akka team |
| **Testing** | Manual HTTP mocking | Direct method invocation |

## Recommended Migration Path

### Phase 1: Migrate to Native MCP Client (Quick Win)

**Current Code:**
```java
// EvidenceAgent.java - Current approach
McpClient mcp = new McpClient();
String body = mcp.fetchLogs(service, lines);
// Manual JSON parsing...
```

**Migrated Code:**
```java
// EvidenceAgent.java - Native approach
public Effect<String> gather(Request req) {
    return effects()
        .model(ModelProvider.openAi()...)
        .memory(MemoryProvider.limitedWindow())
        .mcpTools(
            RemoteMcpTools.fromService("evidence-tools")
                .withAllowedToolNames("fetch_logs", "query_metrics", "correlate_evidence")
        )
        // REMOVE local @FunctionTool methods - they become MCP tools instead
        .systemMessage(SYSTEM)
        .userMessage(contextualPrompt)
        .thenReply();
}
```

### Phase 2: Expose Tools as MCP Endpoint

**Create New Endpoint:**
```java
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@McpEndpoint(serverName = "evidence-tools", serverVersion = "1.0.0")
public class EvidenceToolsEndpoint {

    @McpTool(name = "fetch_logs", description = "Fetch service logs for analysis")
    public String fetchLogs(
        @Description("Service name") String service,
        @Description("Number of lines") int lines
    ) {
        // Move implementation from EvidenceAgent.fetchLogs()
        String fileName = String.format("logs/%s.log", service);
        InputStream in = getClass().getClassLoader().getResourceAsStream(fileName);
        if (in == null) {
            return String.format("{\"error\":\"No log file found for service: %s\"}", service);
        }
        String logs = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        // Return JSON-formatted logs
        return formatLogsAsJson(logs, service, lines);
    }

    @McpTool(name = "query_metrics", description = "Query performance metrics")
    public String queryMetrics(
        @Description("Metrics expression") String expr,
        @Description("Time range") String range
    ) {
        // Move implementation from EvidenceAgent.queryMetrics()
        String fileName = determineMetricsFile(expr);
        InputStream in = getClass().getClassLoader().getResourceAsStream(fileName);
        if (in == null) {
            return String.format("{\"error\":\"No metrics found for: %s\"}", expr);
        }
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    @McpResource(
        uriTemplate = "kb://runbooks/{serviceName}",
        name = "Service Runbook",
        description = "Get runbook for a specific service",
        mimeType = "text/markdown"
    )
    public String getRunbook(String serviceName) {
        String path = String.format("knowledge_base/%s-runbook.md", serviceName);
        InputStream in = getClass().getClassLoader().getResourceAsStream(path);
        if (in == null) return "# Runbook not found";
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
```

### Phase 3: Remove Custom MCP Code

**Files to Delete:**
- `src/main/java/com/example/sample/application/McpClient.java`
- `src/main/java/com/example/sample/mock/McpMockEndpoint.java`

**Files to Refactor:**
- `EvidenceAgent.java` - Remove `@FunctionTool` methods, use `.mcpTools()` instead
- `TriageAgent.java` - If using MCP, refactor similarly
- `application.conf` - Remove custom `mcp.http.url` config

## Benefits of Migration

### 1. Cleaner Code
- **Before:** 600+ lines across `McpClient.java`, `McpMockEndpoint.java`, `EvidenceAgent.java`
- **After:** ~100 lines in `EvidenceToolsEndpoint.java`, declarative `.mcpTools()` in agents

### 2. Better Separation of Concerns
- **Tools:** Dedicated `@McpEndpoint` classes (stateless, reusable)
- **Agents:** Focus on AI reasoning, not tool implementation
- **Resources:** Knowledge base documents exposed as MCP resources

### 3. Interoperability
- Other Akka services can consume your tools via MCP
- External MCP clients (Claude Desktop, VSCode extensions) can use your tools
- Your agents can consume third-party MCP servers

### 4. Production-Ready Features
- Built-in authentication/authorization
- Proper error handling (JSON-RPC error objects)
- Timeout management
- Security via ACLs and tool interceptors
- Monitoring and observability

### 5. Future-Proof
- Maintained by Akka team
- Protocol updates handled automatically
- No custom code to maintain

## Configuration Changes

### Before (Custom Implementation)
```hocon
# application.conf
mcp.http.url = "http://localhost:9100/mcp"
```

### After (Native Support)
```java
// In agent code - no config needed!
.mcpTools(
    RemoteMcpTools.fromService("evidence-tools"),  // Auto-discovers at /mcp
    RemoteMcpTools.fromServer(System.getenv("EXTERNAL_MCP_URL"))
        .addClientHeader(HttpHeader.create("Authorization", "Bearer " + apiKey))
)
```

## Testing Approach

### Before (Custom)
```java
// WorkflowTest.java - Manual mocking
TestModelProvider mockProvider = TestModelProvider.of("mock response");
```

### After (Native)
```java
// Direct invocation of MCP endpoint methods
@Test
void testFetchLogs() {
    EvidenceToolsEndpoint endpoint = new EvidenceToolsEndpoint();
    String result = endpoint.fetchLogs("payment-service", 100);
    assertThat(result).contains("payment-service");
}

// Or use HTTP client for integration tests
@Test
void testMcpEndpoint() {
    String jsonRpc = """
        {
          "jsonrpc": "2.0",
          "id": "1",
          "method": "call_tool",
          "params": {
            "name": "fetch_logs",
            "arguments": {"service": "payment-service", "lines": 100}
          }
        }
        """;

    HttpResponse<String> response = httpClient.send(
        HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:9100/mcp"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(jsonRpc))
            .build(),
        HttpResponse.BodyHandlers.ofString()
    );

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode result = mapper.readTree(response.body());
    assertThat(result.get("result").get("logs")).isNotNull();
}
```

## Documentation References

- **MCP Endpoints Guide:** https://doc.akka.io/java/mcp-endpoints.html
- **Agent Implementation:** https://doc.akka.io/java/agents.html
- **RemoteMcpTools API:** https://doc.akka.io/java/_attachments/api/akka/javasdk/agent/RemoteMcpTools.html
- **MCP Specification:** https://modelcontextprotocol.io/specification/2025-03-26
- **Akka MCP Blog:** https://akka.io/blog/akka-agents-quickly-create-agents-mcp-grpc-api

## Next Steps

1. ✅ Create `mcp-updates` branch
2. ⏳ Create proof-of-concept `EvidenceToolsEndpoint` with native annotations
3. ⏳ Refactor `EvidenceAgent` to use `.mcpTools()` instead of custom `McpClient`
4. ⏳ Test MCP endpoint with curl/HTTP client
5. ⏳ Migrate other agents if applicable
6. ⏳ Remove custom MCP implementation files
7. ⏳ Update documentation and tests
8. ⏳ Create PR with migration

## Conclusion

The custom MCP bridge implementation was a reasonable approach before discovering Akka's native support, but **migrating to native MCP support will**:

- ✅ Reduce code by ~500+ lines
- ✅ Improve type safety and maintainability
- ✅ Enable interoperability with external MCP clients
- ✅ Provide production-ready features (auth, timeouts, interceptors)
- ✅ Future-proof the implementation

**Recommendation:** Proceed with migration in phases, starting with Phase 1 (agent-side `.mcpTools()`) as a proof-of-concept.

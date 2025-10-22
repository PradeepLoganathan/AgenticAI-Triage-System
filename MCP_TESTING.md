# Testing the MCP Endpoint

This guide shows you how to run and test the native Akka MCP endpoint independently.

## Quick Start

### 1. Start the Service

```bash
# Set a dummy API key (agents won't work, but MCP endpoint will)
export OPENAI_API_KEY="sk-dummy-key-not-needed-for-mcp-endpoint"

# Start the service
mvn compile exec:java
```

The service will start on **port 9100**. Wait for this log message:
```
INFO  akka.runtime.DiscoveryManager - Akka Runtime started at 127.0.0.1:9100
```

### 2. Run the Test Suite

```bash
# Make the test script executable (first time only)
chmod +x test-mcp.sh

# Run all MCP tests
./test-mcp.sh
```

This will test all 3 MCP tools and show formatted output.

## Manual Testing with curl

### List Available Tools

```bash
curl -s http://localhost:9100/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/list",
    "params": {}
  }' | python3 -m json.tool
```

**Response:**
```json
{
    "jsonrpc": "2.0",
    "id": "1",
    "result": {
        "tools": [
            {
                "name": "correlate_evidence",
                "description": "Correlate findings across different evidence sources",
                "inputSchema": { ... }
            },
            {
                "name": "fetch_logs",
                "description": "Fetch service logs for incident analysis",
                "inputSchema": { ... }
            },
            {
                "name": "query_metrics",
                "description": "Query performance metrics for incident analysis",
                "inputSchema": { ... }
            }
        ]
    }
}
```

### Call fetch_logs Tool

```bash
curl -s http://localhost:9100/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/call",
    "params": {
      "name": "fetch_logs",
      "arguments": {
        "service": "payment-service",
        "lines": 50
      }
    }
  }' | python3 -m json.tool
```

**Response:**
```json
{
    "jsonrpc": "2.0",
    "id": "2",
    "result": {
        "content": [
            {
                "type": "text",
                "text": "{\n  \"logs\": \"...\",\n  \"analysis\": {...}\n}"
            }
        ],
        "isError": false
    }
}
```

### Call query_metrics Tool

```bash
curl -s http://localhost:9100/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "3",
    "method": "tools/call",
    "params": {
      "name": "query_metrics",
      "arguments": {
        "expr": "payment error_rate",
        "range": "1h"
      }
    }
  }' | python3 -m json.tool
```

### Call correlate_evidence Tool

```bash
curl -s http://localhost:9100/mcp \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "4",
    "method": "tools/call",
    "params": {
      "name": "correlate_evidence",
      "arguments": {
        "logFindings": "High rate of HTTP 503 errors",
        "metricFindings": "CPU spike to 95% at 14:30"
      }
    }
  }' | python3 -m json.tool
```

## Available MCP Methods

The endpoint supports these JSON-RPC 2.0 methods:

| Method | Description | Example Params |
|--------|-------------|----------------|
| `tools/list` | List all available tools | `{}` |
| `tools/call` | Call a specific tool | `{"name": "fetch_logs", "arguments": {...}}` |
| `resources/list` | List available resources | `{}` |
| `resources/read` | Read a specific resource | `{"uri": "kb://runbooks/payment-service"}` |

## MCP Tools Provided

### 1. fetch_logs
**Description:** Fetch service logs for incident analysis with error analysis

**Arguments:**
- `service` (string) - Service name (e.g., "payment-service", "checkout-service")
- `lines` (integer) - Number of log lines to fetch

**Returns:**
- Raw logs
- Error count and patterns
- HTTP status code counts
- Anomaly detection
- Sample error lines

**Available Services:**
- payment-service
- checkout-service
- auth-service
- api-gateway
- order-service
- user-service

### 2. query_metrics
**Description:** Query performance metrics with automatic parsing and insights

**Arguments:**
- `expr` (string) - Metrics expression (e.g., "error_rate", "latency", "cpu_usage")
- `range` (string) - Time range (e.g., "1h", "30m", "5m")

**Returns:**
- Raw metrics JSON
- Formatted summary
- Key insights
- Source information

### 3. correlate_evidence
**Description:** Correlate findings across logs and metrics

**Arguments:**
- `logFindings` (string) - Description of log findings
- `metricFindings` (string) - Description of metric findings

**Returns:**
- Potential correlations
- Confidence assessment
- Analysis recommendations

## Integration with AI Agents

The MCP endpoint can be consumed by:

1. **Akka Agents** (via `RemoteMcpTools.fromService()`)
   ```java
   .mcpTools(
       RemoteMcpTools.fromService("evidence-tools")
           .withAllowedToolNames("fetch_logs", "query_metrics")
   )
   ```

2. **External MCP Clients**
   - Claude Desktop
   - VSCode with MCP extensions
   - Other Akka services
   - Custom MCP clients

3. **Direct HTTP Calls**
   - curl (as shown above)
   - Postman
   - Any HTTP client supporting JSON-RPC 2.0

## Troubleshooting

### Service won't start
- Check if port 9100 is available: `lsof -i :9100`
- Ensure OPENAI_API_KEY is set (even a dummy value works for MCP endpoint)
- Check logs: `mvn compile exec:java 2>&1 | tee app.log`

### Tools not responding
- Verify service is running: `curl http://localhost:9100/mcp`
- Check JSON syntax in your request
- Ensure correct method name: `tools/call` (not `call_tool`)

### Missing log/metrics files
The MCP endpoint reads from `src/main/resources/`:
- Logs: `logs/` directory
- Metrics: `metrics/` directory
- Knowledge base: `knowledge_base/` directory

## Learn More

- [MCP Specification](https://modelcontextprotocol.io/specification/2025-03-26)
- [Akka MCP Documentation](https://doc.akka.io/java/mcp-endpoints.html)
- [Migration Findings](docs/MCP_NATIVE_SUPPORT_FINDINGS.md)
- [Test Cases](docs/mcp-native-test.http)

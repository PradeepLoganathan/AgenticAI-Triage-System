#!/bin/bash

# MCP Endpoint Test Script
# Tests the native Akka MCP endpoint for evidence tools

BASE_URL="http://localhost:9100/mcp"

echo "========================================="
echo "MCP Endpoint Test Suite"
echo "========================================="
echo ""

# Color codes for output

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Test 1: List available tools
echo -e "${BLUE}Test 1: List Available Tools${NC}"
echo "Command: tools/list"
echo ""
curl -s "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "1",
    "method": "tools/list",
    "params": {}
  }' | python3 -m json.tool

echo ""
echo "========================================="
echo ""

# Test 2: Call fetch_logs tool
echo -e "${BLUE}Test 2: Fetch Logs Tool${NC}"
echo "Command: tools/call -> fetch_logs"
echo "Arguments: service=payment-service, lines=30"
echo ""
curl -s "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "2",
    "method": "tools/call",
    "params": {
      "name": "fetch_logs",
      "arguments": {
        "service": "payment-service",
        "lines": 30
      }
    }
  }' | python3 -m json.tool | head -50

echo ""
echo "========================================="
echo ""

# Test 3: Call query_metrics tool
echo -e "${BLUE}Test 3: Query Metrics Tool${NC}"
echo "Command: tools/call -> query_metrics"
echo "Arguments: expr=payment error_rate, range=1h"
echo ""
curl -s "$BASE_URL" \
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
  }' | python3 -m json.tool | head -40

echo ""
echo "========================================="
echo ""

# Test 4: Call correlate_evidence tool
echo -e "${BLUE}Test 4: Correlate Evidence Tool${NC}"
echo "Command: tools/call -> correlate_evidence"
echo ""
curl -s "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "4",
    "method": "tools/call",
    "params": {
      "name": "correlate_evidence",
      "arguments": {
        "logFindings": "High rate of HTTP 503 errors and database timeouts",
        "metricFindings": "CPU spike to 95% at 14:30 UTC, memory at 87%"
      }
    }
  }' | python3 -m json.tool

echo ""
echo "========================================="
echo ""

# Test 5: List resources
echo -e "${BLUE}Test 5: List Available Resources${NC}"
echo "Command: resources/list"
echo ""
curl -s "$BASE_URL" \
  -H "Content-Type: application/json" \
  -d '{
    "jsonrpc": "2.0",
    "id": "5",
    "method": "resources/list",
    "params": {}
  }' | python3 -m json.tool

echo ""
echo "========================================="
echo -e "${GREEN}All tests completed!${NC}"
echo "========================================="

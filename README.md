# Agentic Triage System

A production-ready multi-agent incident triage system orchestrated by Akka Java SDK Workflow with comprehensive guardrails, LLM-as-a-Judge evaluation, and real-time dashboards.

## Overview

This system demonstrates enterprise-grade AI agent orchestration with:
- **Multi-Agent Workflow**: 6 specialized AI agents collaborating to analyze incidents
- **Security Guardrails**: 4 layers of protection (PII, Profanity, Prompt Injection, Data Leakage)
- **Quality Evaluation**: LLM-as-a-Judge for toxicity and hallucination detection
- **Real-time Dashboards**: Workflow visualization, incident tracking, and evaluation metrics
- **MCP Integration**: Native Model Context Protocol for external tool/resource access
- **Memory Management**: Session-based context with state size tracking

## Core Components

### Agents
- **ClassifierAgent**: Classifies incident (service, severity, domain) with structured reasoning and confidence scores
- **EvidenceAgent**: Gathers evidence via native MCP tools from external service (`fetch_logs`, `query_metrics`, `correlate_evidence`)
- **TriageAgent**: Performs systematic diagnosis using 5 Whys methodology, impact assessment, and pattern analysis
- **KnowledgeBaseAgent**: Accesses service runbooks via MCP resources from external evidence-tools service
- **RemediationAgent**: Proposes risk-aware staged remediation plans with rollback strategies
- **SummaryAgent**: Produces multi-audience summaries (executive, technical, customer support)

### Orchestration
- **TriageWorkflow**: Orchestrates the complete workflow: classify → gather_evidence → triage → query_knowledge_base → remediate → summarize → finalize
- **Error Recovery**: Includes failover strategies (evidence failures → continue to triage, remediation failures → skip to summary)

### HTTP Endpoints
- **TriageEndpoint**: REST API for starting workflows and retrieving conversations/state
- **UiEndpoint**: Serves interactive web UI for real-time workflow visualization
- **IncidentDashboardEndpoint**: Provides incident metrics and dashboard data
- **EvaluationEndpoint**: Exposes evaluation metrics and results

## Quick Start

### Prerequisites
- Java 21+
- Maven 3.8+
- Set environment variable: `export OPENAI_API_KEY=your-key-here`
- Ports 9100, 9200, 9300 available

### Run All Services

**IMPORTANT**: This system requires **THREE services** to run:

```bash
# Terminal 1: Evidence MCP Server (port 9200)
cd evidence-mcp-server
mvn compile exec:java

# Terminal 2: Knowledge Base MCP Server (port 9300)
cd knowledge-base-mcp-server
mvn compile exec:java

# Terminal 3: Agentic Triage System (port 9100)
cd agentic-triage-system
export OPENAI_API_KEY=your-key-here
mvn compile exec:java
```

All three services will discover each other automatically in dev-mode.

### Web UI
Open your browser to `http://localhost:9100` for the interactive demo interface featuring:
- **Workflow Tab**: Real-time workflow visualization with agent outputs
- **Incident Dashboard**: Live incident metrics and tracking
- **Evaluation Metrics**: LLM-as-a-Judge quality scores

### Demo Scenarios
The UI includes pre-loaded scenarios:
- **Real Incidents**: Payment Outage (P1), Database Slow (P2), Auth Errors (P2), Deployment Issues (P3)
- **Guardrail Tests**: Profanity, Prompt Injection (3 variants), Data Leakage (5 variants)

## HTTP API

### Triage Endpoints
```bash
# Start a triage workflow
POST /triage/{triageId}
Content-Type: application/json
{
  "incident": "Payment service is down. Users getting 503 errors..."
}

# Get conversation history
GET /triage/{triageId}

# Get detailed state
GET /triage/{triageId}/state

# Add demo context entries (for memory testing)
POST /triage/{triageId}/repeat
Content-Type: application/json
{
  "message": "demo note",
  "times": 5
}
```

### Dashboard Endpoints
```bash
# Get incident statistics
GET /dashboard/stats

# Get all incidents
GET /dashboard/incidents

# Get active incidents
GET /dashboard/incidents/active

# Get incidents by severity
GET /dashboard/incidents/severity/{severity}

# Get critical incidents (P1 only)
GET /dashboard/incidents/critical
```

### Evaluation Endpoints
```bash
# Get evaluation statistics
GET /evaluations/stats

# Get all evaluation results
GET /evaluations
```

## Security Guardrails

The system implements 4 layers of guardrails to ensure safe and compliant AI agent operation:

### 1. PiiGuardrail (`PiiGuardrail.java`)
Detects and blocks Personally Identifiable Information in agent inputs and outputs:
- **Email addresses**: Regex pattern matching
- **Phone numbers**: US/international formats
- **Credit card numbers**: 4-digit groups with separators
- **Social Security Numbers**: XXX-XX-XXXX format
- **IP addresses**: IPv4 and IPv6
- **Passport numbers**: Alphanumeric patterns
- **Driver's license numbers**: State-specific patterns

**Agent Context Awareness**: The guardrail identifies which agent triggered the violation and notes when PII comes from MCP tool/resource calls (e.g., KnowledgeBaseAgent).

### 2. ProfanityGuardrail (`ProfanityGuardrail.java`)
Blocks toxic and offensive language:
- **Profanity detection**: Comprehensive word list matching
- **Context-aware**: Checks both user input and agent outputs
- **HTTP 400 response**: Returns `GUARDRAIL_VIOLATION` error with specific message

### 3. PromptInjectionGuardrail (`PromptInjectionGuardrail.java`)
Prevents attempts to manipulate agent behavior:
- **Instruction override patterns**: "ignore previous instructions", "disregard previous"
- **Role manipulation**: "you are now", "act as if", "pretend you are"
- **System prompts**: "system:", "override instructions"
- **Security bypass**: "forget previous", "new instructions"

### 4. DataLeakageGuardrail (`DataLeakageGuardrail.java`)
Prevents exposure of sensitive credentials and secrets:
- **AWS access keys**: `AKIA...` pattern detection
- **API keys**: `api_key=`, `sk_live_`, `sk_test_` patterns
- **Private keys**: `-----BEGIN PRIVATE KEY-----` detection
- **JWT tokens**: `eyJ...` base64 pattern
- **Database credentials**: Connection string patterns with passwords
- **Generic passwords**: `password=`, `pwd=` patterns

### Guardrail Configuration
Guardrails are applied to agents via the `@Guardrails` annotation:

```java
@Guardrails({
    PiiGuardrail.class,
    ProfanityGuardrail.class,
    PromptInjectionGuardrail.class,
    DataLeakageGuardrail.class
})
@Component(id = "knowledge-base-agent")
public class KnowledgeBaseAgent extends Agent { ... }
```

### Testing Guardrails
Use the included `guard-requests.http` file to test all guardrails:
```bash
# See guard-requests.http for 14 test scenarios covering:
# - Profanity detection
# - Prompt injection (3 variants)
# - Data leakage (5 variants: AWS, API keys, DB passwords, private keys, JWT)
# - PII detection (email, phone, credit card)
# - Combined violations
```

## LLM-as-a-Judge Evaluation

The system includes automated quality evaluation using LLM-as-a-Judge pattern with two evaluation categories:

### 1. Toxicity Evaluation (`ToxicityEvaluator.java`)
Assesses agent outputs for harmful or unprofessional content:
- **Summary Toxicity**: Evaluates the final summary for toxic language
- **Remediation Toxicity**: Checks remediation plans for inappropriate content
- **Confidence Scores**: Returns confidence level (0-1) of the assessment
- **Pass Criteria**: Content must be professional and non-harmful

**Interpretation**: A score of 85% confidence means "85% confident there is NO toxicity" (not "85% toxic").

### 2. Hallucination Evaluation (`HallucinationEvaluator.java`)
Checks agent outputs for factual accuracy against source data:
- **Evidence Hallucination**: Verifies evidence matches incident description
- **Triage Hallucination**: Validates triage analysis is grounded in evidence
- **Summary Hallucination**: Ensures summary accurately reflects all context
- **Confidence Scores**: Returns confidence level (0-1) of the assessment
- **Pass Criteria**: All facts must be traceable to source data

### Evaluation Workflow
1. **Trigger**: `EvaluationConsumer` subscribes to workflow state changes
2. **Timing**: Evaluation runs when workflow reaches `SUMMARY_READY` or `COMPLETED` status
3. **Execution**: Both evaluators run in parallel via `Effect.asyncEffect()`
4. **Storage**: Results stored in `EvaluationResults` entity
5. **Dashboard**: View metrics at `http://localhost:9100/evaluations.html`

### Evaluation Metrics Dashboard
The evaluation dashboard displays:
- **Overall Metrics**: Total evaluations, pass rate, all-checks-passed count
- **Toxicity Metrics**: Pass counts for summary and remediation, average confidence
- **Hallucination Metrics**: Pass counts for evidence, triage, and summary, average confidence
- **Detailed Results**: Per-workflow pass/fail status with explanations

### Testing Evaluation
```bash
# Run a triage workflow
POST http://localhost:9100/triage/test-001
Content-Type: application/json
{
  "incident": "Payment service outage..."
}

# Wait for completion, then check evaluation results
GET http://localhost:9100/evaluations/stats
GET http://localhost:9100/evaluations
```

## MCP Configuration

This system uses **TWO separate MCP servers** to demonstrate multi-system integration:

### 1. Evidence MCP Server (Port 9200)
**Purpose**: Operational data (logs, metrics)
**Represents**: Splunk, Datadog, Prometheus, CloudWatch

**EvidenceAgent Configuration:**
```java
.mcpTools(
    RemoteMcpTools.fromService("evidence-mcp-server")
        .withAllowedToolNames("fetch_logs", "query_metrics", "correlate_evidence")
)
```

**Tools Available:**
- `fetch_logs`: Get service logs with error analysis
- `query_metrics`: Query performance metrics
- `correlate_evidence`: Analyze relationships between evidence

### 2. Knowledge Base MCP Server (Port 9300)
**Purpose**: Documentation and runbooks
**Represents**: Confluence, SharePoint, Jira, ServiceNow

**KnowledgeBaseAgent Configuration:**
```java
.mcpResources(
    RemoteMcpResources.fromService("knowledge-base-mcp-server")
        .withAllowedResourceUriPatterns("kb://runbooks/*")
)
```

**Resources Available:**
- `kb://runbooks/{serviceName}`: Service-specific troubleshooting runbooks
- **Contains PII** (email, phone) for guardrail demonstration

### Service Discovery

Both MCP servers are discovered automatically in dev-mode:

**Evidence MCP Server:**
```hocon
akka.javasdk.dev-mode {
  service-name = "evidence-mcp-server"
  http-port = 9200
}
```

**Knowledge Base MCP Server:**
```hocon
akka.javasdk.dev-mode {
  service-name = "knowledge-base-mcp-server"
  http-port = 9300
}
```

### Starting MCP Servers

```bash
# Terminal 1: Evidence MCP Server
cd evidence-mcp-server
mvn compile exec:java
# → Started on http://localhost:9200

# Terminal 2: Knowledge Base MCP Server
cd knowledge-base-mcp-server
mvn compile exec:java
# → Started on http://localhost:9300

# Terminal 3: Agentic Triage System
cd agentic-triage-system
export OPENAI_API_KEY=your-key
mvn compile exec:java
# → Started on http://localhost:9100
```

### Demo Benefits of Split Architecture

1. **Enterprise Realism**: No company has one system for everything - demonstrates real-world integration
2. **Clear Separation**: Observability (Evidence) vs Knowledge Management (KB)
3. **Better Guardrail Demo**: PII source is clearly from KB server, not Evidence server
4. **Failure Scenarios**: Can demo Evidence server working while KB server is blocked by guardrails
5. **Scalability**: Each server can be scaled independently




## State Management & Event Consumers

### TriageState Entity
The workflow maintains state in a Key-Value Entity:
- **Incident data**: Original incident description
- **Agent outputs**: Classification, evidence, triage analysis, knowledge base results, remediation, summary
- **Memory metrics**: Context entries, approximate state size (chars), heap usage (bytes)
- **Session tracking**: Agent session ID for bounded context window
- **Status progression**: EMPTY → PREPARED → CLASSIFIED → EVIDENCE_COLLECTED → TRIAGED → KNOWLEDGE_BASE_SEARCHED → REMEDIATION_PROPOSED → SUMMARY_READY → COMPLETED

### Event Consumers
The system uses event-driven consumers to track workflow progress:

#### 1. IncidentMetricsConsumer
**Purpose**: Tracks incident metrics for dashboard visualization

**Subscription**: Subscribes to state changes of `TriageState` entity

**Processing**:
- Extracts service, severity, status from workflow state
- Calculates step progress (0-7)
- Updates two entities:
  - `IncidentMetrics` (individual entity per workflow)
  - `IncidentRegistry` (centralized registry at ID "global")

**Dashboard Integration**: Powers the Incident Dashboard with real-time metrics

#### 2. EvaluationConsumer
**Purpose**: Triggers LLM-as-a-Judge evaluation when workflow completes

**Subscription**: Subscribes to state changes of `TriageState` entity

**Trigger Conditions**: Only evaluates when status is `SUMMARY_READY` or `COMPLETED`

**Processing**:
- Runs `ToxicityEvaluator` and `HallucinationEvaluator` in parallel
- Stores results in `EvaluationResults` entity
- Updates `EvaluationStats` entity with aggregated metrics

**Evaluation Integration**: Powers the Evaluation Metrics dashboard

### IncidentRegistry Pattern
**Challenge**: Querying all incidents from a View using streaming APIs was complex

**Solution**: Centralized registry entity (ID="global") that maintains a list of ALL incidents

**Benefits**:
- Simple REST API queries (no streaming needed)
- Fast dashboard loading
- Easy filtering by service, severity, active status

## Web UI Features

### 1. Workflow Visualization (`index.html`)
Interactive workflow dashboard with:
- **Demo Scenario Dropdown**: 13 pre-loaded scenarios (4 real incidents + 9 guardrail tests)
- **Real-time Progress**: Visual step-by-step workflow progress indicator
- **Agent Cards**: Collapsible cards showing input/output for each agent
- **Live Metrics**: Total time, completed steps, severity, context entries, state size, heap usage
- **Memory Testing**: Add demo context entries to test memory management
- **Export**: Download complete workflow results as JSON

### 2. Incident Dashboard (`dashboard.html`)
Live incident tracking dashboard:
- **Statistics Cards**: Total incidents, active count, P1/P2 counts, escalations, average progress
- **Incident Table**: Filterable table with incident ID, service, severity, status, progress, team, start time
- **Color Coding**: Severity badges (P1=danger, P2=warning, P3=info)
- **Status Indicators**: Active vs. completed status badges

### 3. Evaluation Metrics (`evaluations.html`)
LLM-as-a-Judge quality dashboard:
- **Summary Section**: Total evaluations, all-passed count, overall pass rate
- **Toxicity Section**: Summary toxicity, remediation toxicity, average confidence
- **Hallucination Section**: Evidence factual, triage factual, summary factual, average confidence
- **Results Table**: Per-workflow pass/fail status for all 5 checks
- **Expandable Details**: Click rows to see detailed evaluation explanations

## Memory Management

### Session-Based Context
- **Shared Session ID**: All agents in a workflow share the same session ID for context continuity
- **Bounded Context Window**: Prevents unlimited context growth
- **Memory Modes**: Configurable via agent settings (e.g., `SUMMARIZATION`, `SLIDING_WINDOW`)

### State Size Tracking
The system tracks memory usage in real-time:
- **Context Entries**: Number of conversation turns in agent memory
- **State Size**: Approximate characters in serialized state
- **Heap Metrics**: Used, committed, and max heap sizes (bytes)

### Testing Memory Growth
Use the "Add demo notes" feature to simulate context growth:
```bash
POST /triage/{triageId}/repeat
{
  "message": "Context growth test",
  "times": 10
}
```

## Error Handling & Retry Strategy

### Workflow Failover
The workflow includes intelligent error recovery:

1. **Evidence Gathering Failure**
   - If `EvidenceAgent` fails (MCP errors, timeout)
   - Workflow continues to `TriageAgent` with classification only
   - Triage proceeds with limited context

2. **Knowledge Base Failure**
   - If `KnowledgeBaseAgent` fails (MCP errors, PII guardrail)
   - Workflow continues to `RemediationAgent` without KB context
   - Remediation uses incident + classification + evidence + triage

3. **Remediation Failure**
   - If `RemediationAgent` fails
   - Workflow skips directly to `SummaryAgent`
   - Summary generated with available context

### Guardrail Failures
When a guardrail blocks agent output:
- **Input Guardrails**: Return HTTP 400 immediately (e.g., profanity in incident description)
- **Output Guardrails**: Agent fails, workflow may continue depending on step
- **PII in MCP Results**: Blocks KnowledgeBaseAgent, workflow continues without KB context

## Technical Details

### Technology Stack
- **Akka Java SDK**: 3.5.6 (with native MCP support)
- **Java**: 21+
- **LLM Provider**: OpenAI (`gpt-4o-mini` for agents, `gpt-4o` for evaluation)
- **Build Tool**: Maven 3.8+
- **Frontend**: Vanilla JavaScript, HTML, CSS (no framework)

### Model Configuration
Configured in `src/main/resources/application.conf`:
```hocon
akka.javasdk {
  agent {
    model-provider = "openai"
    model-name = "gpt-4o-mini"
  }
}
```

### OpenTelemetry
Telemetry is disabled by default (configured in `pom.xml`):
```xml
<environmentVariables>
  <OTEL_TRACES_EXPORTER>none</OTEL_TRACES_EXPORTER>
  <OTEL_METRICS_EXPORTER>none</OTEL_METRICS_EXPORTER>
  <OTEL_LOGS_EXPORTER>none</OTEL_LOGS_EXPORTER>
</environmentVariables>
```

### Agent Behavior Patterns
- **ClassifierAgent**: Single-shot classification with JSON output
- **EvidenceAgent**: MCP tool orchestration (fetch_logs, query_metrics, correlate_evidence)
- **TriageAgent**: Function tools for systematic analysis (assess_impact, analyze_patterns)
- **KnowledgeBaseAgent**: MCP resource access for runbook retrieval
- **RemediationAgent**: Risk-aware planning with staged rollout strategies
- **SummaryAgent**: Multi-audience formatting (executive, technical, support)

## Architecture (Mermaid)

```mermaid
flowchart LR
  Browser[Web UI] -->|HTTP| UI[UiEndpoint]
  Browser -->|HTTP| API[TriageEndpoint]
  API -->|start/get| WF[TriageWorkflow]
  WF -->|classify| CA[ClassifierAgent]
  WF -->|gather_evidence| EA[EvidenceAgent]
  EA -->|RemoteMcpTools| ExtMCP[(External MCP Server)]
  WF -->|triage| TA[TriageAgent]
  TA -->|@FunctionTool| TA
  WF -->|query_kb| KBA[KnowledgeBaseAgent]
  KBA -->|@FunctionTool| KB[(Local Knowledge Base)]
  WF -->|remediate| RA[RemediationAgent]
  WF -->|summarize| SA[SummaryAgent]
  WF -->|update| State[(TriageState)]
```

## Workflow Sequence

```mermaid
sequenceDiagram
  participant UI as Web UI
  participant API as TriageEndpoint
  participant WF as TriageWorkflow
  participant CA as ClassifierAgent
  participant EA as EvidenceAgent
  participant ExtMCP as External MCP
  participant TA as TriageAgent
  participant KBA as KnowledgeBaseAgent
  participant RA as RemediationAgent
  participant SA as SummaryAgent

  UI->>API: POST /triage/{id} { incident }
  API->>WF: start(incident)
  WF->>CA: classify(incident)
  CA-->>WF: classification JSON
  WF->>EA: gather(service, expr, range)
  EA->>ExtMCP: tools/call fetch_logs
  ExtMCP-->>EA: logs
  EA->>ExtMCP: tools/call query_metrics
  ExtMCP-->>EA: metrics
  EA->>ExtMCP: tools/call correlate_evidence
  ExtMCP-->>EA: correlations
  EA-->>WF: evidence JSON
  WF->>TA: triage(enriched context)
  TA-->>WF: triage analysis
  WF->>KBA: search(service)
  KBA-->>WF: knowledge base results
  WF->>RA: remediate(all context)
  RA-->>WF: remediation plan
  WF->>SA: summarize(all context)
  SA-->>WF: multi-audience summary
  WF-->>API: completed
  UI->>API: GET /triage/{id}
  API-->>UI: conversations + state
```


## Project Structure

```
agentic-triage-system/
├── src/main/java/com/pradeepl/triage/
│   ├── agents/              # AI agents
│   │   ├── ClassifierAgent.java
│   │   ├── EvidenceAgent.java
│   │   ├── TriageAgent.java
│   │   ├── KnowledgeBaseAgent.java
│   │   ├── RemediationAgent.java
│   │   └── SummaryAgent.java
│   ├── workflow/            # Workflow orchestration
│   │   └── TriageWorkflow.java
│   ├── application/         # State & entities
│   │   ├── TriageState.java
│   │   ├── IncidentMetrics.java
│   │   ├── IncidentRegistry.java
│   │   ├── EvaluationResults.java
│   │   └── EvaluationStats.java
│   ├── consumers/           # Event consumers
│   │   ├── IncidentMetricsConsumer.java
│   │   └── EvaluationConsumer.java
│   ├── evaluation/          # LLM-as-a-Judge evaluators
│   │   ├── ToxicityEvaluator.java
│   │   └── HallucinationEvaluator.java
│   ├── guardrails/          # Security guardrails
│   │   ├── PiiGuardrail.java
│   │   ├── ProfanityGuardrail.java
│   │   ├── PromptInjectionGuardrail.java
│   │   └── DataLeakageGuardrail.java
│   └── endpoints/           # HTTP endpoints
│       ├── TriageEndpoint.java
│       ├── UiEndpoint.java
│       ├── IncidentDashboardEndpoint.java
│       └── EvaluationEndpoint.java
├── src/main/resources/
│   ├── application.conf     # Akka configuration
│   └── static-resources/    # Web UI files
│       ├── index.html       # Workflow visualization
│       ├── dashboard.html   # Incident dashboard
│       ├── evaluations.html # Evaluation metrics
│       ├── app.js           # Main JavaScript
│       ├── dashboard.js     # Dashboard logic
│       ├── evaluations.js   # Evaluation logic
│       └── styles.css       # UI styling
└── guard-requests.http      # Guardrail test requests
```

## Best Practices & Tips

### Demo Workflow
1. **Start Simple**: Use "Payment Service Outage (P1)" scenario to see full workflow
2. **Test Guardrails**: Try guardrail scenarios to see security in action
3. **Monitor Progress**: Watch agent cards expand as each step completes
4. **Check Evaluations**: Navigate to Evaluation Metrics after workflow completes
5. **Test Memory**: Use "Add demo notes" to see context growth and state size increase

### Guardrail Testing
- **Profanity**: Tests basic toxic language filtering
- **Prompt Injection**: Demonstrates protection against manipulation attempts
- **Data Leakage**: Shows credential and secret detection
- **PII**: Use scenarios with email/phone, then check Knowledge Base agent for MCP-sourced PII

### Evaluation Interpretation
- **Pass Rate < 80%**: Investigate agent prompts for quality issues
- **Low Confidence Scores**: May indicate unclear evaluation criteria
- **Hallucination Failures**: Check if agents are grounding responses in source data
- **Toxicity Failures**: Review agent system prompts for tone guidance

### Troubleshooting
- **Workflow Stuck**: Check logs for guardrail violations or MCP errors
- **Empty Dashboards**: Ensure workflows have completed at least once
- **Evaluation Not Running**: Verify workflow reached `SUMMARY_READY` or `COMPLETED` status
- **MCP Errors**: Confirm external MCP server is running and accessible

## Sample Incident Scenarios

### P1 - Payment Service Outage
```
Payment service is completely down since 14:30 UTC. Users are unable to complete
transactions and getting 503 errors. Multiple customer complaints received via
social media and support tickets. Revenue impact is significant.

Error details:
- Payment gateway returning 503 Service Unavailable
- Database connection timeouts in payment-service logs
- CPU usage spiked to 95% on payment-db-primary
- Recent deployment of payment-service v2.1.4 at 14:25 UTC
- Load balancer health checks failing for 3/6 payment service instances

Customer impact:
- 100% of payment transactions failing
- Estimated $50K/hour revenue loss
- 847 support tickets created in last hour
```

### P2 - Database Performance
```
Database performance degradation noticed since 08:00 UTC. Query response times
increased from average 50ms to 2-5 seconds. Intermittent timeouts reported by
multiple services.

Technical details:
- Primary database CPU at 85% consistently
- Disk I/O wait time increased to 40%
- Slow query log showing expensive JOIN operations
- Connection pool exhaustion warnings
- No recent deployments or configuration changes

Service impact:
- checkout-service: 30% slower response times
- user-service: occasional timeout errors
- search-service: degraded performance
```

## Key Features Demonstrated

✅ **Multi-Agent Orchestration**: 6 specialized agents working together
✅ **Workflow Error Recovery**: Intelligent failover on agent failures
✅ **MCP Integration**: Tools and resources from external services
✅ **Security Guardrails**: 4-layer protection (PII, Profanity, Injection, Leakage)
✅ **LLM-as-a-Judge**: Automated quality evaluation (toxicity + hallucination)
✅ **Memory Management**: Session-based context with size tracking
✅ **Real-time Dashboards**: Workflow visualization, incidents, evaluations
✅ **Event-Driven Architecture**: Consumers for metrics and evaluation
✅ **Centralized State**: IncidentRegistry pattern for simple queries

## Additional Resources

- **Akka Java SDK Documentation**: https://doc.akka.io/java/
- **MCP Protocol Specification**: https://spec.modelcontextprotocol.io/
- **OpenAI API Reference**: https://platform.openai.com/docs/api-reference
- **LLM-as-a-Judge Pattern**: Research papers on using LLMs to evaluate LLM outputs

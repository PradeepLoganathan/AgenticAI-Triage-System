# Project Overview

This project is a sample agentic workflow for triaging incidents. It is built using the Akka Java SDK and Maven. The system is composed of multiple agents, each responsible for a specific task in the triage process. A `TriageWorkflow` orchestrates the agents to classify, gather evidence, triage, remediate, and summarize incidents.

The core technologies used are:
- **Java**: The primary programming language.
- **Akka Java SDK**: For building the agent-based system.
- **Maven**: For dependency management and building the project.
- **OpenAI**: The AI models are used by the agents to perform their tasks.

## Building and Running

**Prerequisites:**
- Set the `OPENAI_API_KEY` environment variable.
- Optional: Set `MCP_HTTP_URL` (default `http://localhost:7400/jsonrpc`) to enable MCP tool calls.

**Build:**
```bash
mvn -f spov-sample-agentic-workflow/pom.xml clean package
```

**Run (Dev Mode):**
The application starts on port `9100` (configured in `src/main/resources/application.conf`).

## Development Conventions

- **Agent-based architecture**: The application is structured around agents, each with a specific responsibility.
- **Workflow orchestration**: A central workflow component (`TriageWorkflow`) orchestrates the execution of the agents.
- **Function Tools**: Agents can expose function tools that can be called by the AI model.
- **Testing**: The project uses JUnit and AssertJ for testing. Tests are located in the `src/test/java` directory.

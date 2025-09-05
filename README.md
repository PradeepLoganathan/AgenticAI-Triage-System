# Sample: Agent + Workflow (Triage)

This module demonstrates an Akka Java SDK Agent orchestrated by a Workflow.

## What it does
- `TriageAgent`: LLM-backed triage for incident summaries.
- `TriageWorkflow`: Prepares context, calls the agent, and finalizes.
- `TriageEndpoint`: HTTP interface to start and fetch conversations.

## Run
- Prereq: set `OPENAI_API_KEY`.
- Build: `mvn -f spov-sample-agentic-workflow/pom.xml clean package`
- Dev mode: starts on `:9100` (configured in `src/main/resources/application.conf`).

## HTTP
- Start triage: `POST /triage/{triageId}` with body `{ "incident": "<summary>" }`
- Get conversation: `GET /triage/{triageId}`

## Notes
- Uses `ModelProvider.openAi()` with `gpt-4o-mini`.
- Swap models via `application.conf` or environment.

Memory + State Demo (Triage Workflow)

Goal: Show how the triage workflow manages durable state and how the agent memory window behaves across calls when reusing a stable agent session.

Key ideas
- Stable agent session: The workflow now establishes one `agentSessionId` (stored in state) and reuses it for every agent call. This demonstrates bounded memory accumulation across steps.
- Bounded memory: All agents use `MemoryProvider.limitedWindow()` so the LLM memory remains capped per session.
- State visibility: `/triage/{id}/state` returns session id, conversation count, an approximate state size, and JVM heap stats.
- Agent session reuse is enabled in the workflow; additional interactive helpers (reset/ping) can be added later depending on SDK support for command replies after state updates.

Endpoints
1) Start triage
   curl -s -X POST \
     localhost:9100/triage/demo-1 \
     -H 'content-type: application/json' \
     -d '{"incident":"Checkout errors spike after deploy"}'

2) View state (memory + session info)
   curl -s localhost:9100/triage/demo-1/state | jq

   Returns fields like:
   - agentSessionId
   - contextEntries
   - approxStateChars
   - heapUsedBytes/heapCommittedBytes/heapMaxBytes
   - agentMemoryMode ("LIMITED_WINDOW")

3) Repeat triage start and inspect state
   Start another triage (new id) and compare `/state` outputs — you’ll see distinct `agentSessionId`s and context sizes. All agent calls inside each workflow reuse that session and benefit from the bounded window.

Notes
- The workflow state persists across requests and includes a conversation log for transparency of step transitions.
- The agent memory window is separate and per-session; resetting the session demonstrates that distinction clearly.

# Live reasoning and tool timeline for the AI assistant

Date: 2026-11-01

## Intent

The chat sidebar should show the complete provider-supplied reasoning and every tool call, in execution order, while an agent run is happening. Each reasoning turn and tool call can be expanded or collapsed. The same timeline remains available when a conversation is reopened or the page is refreshed. This is for people using the WGS AI assistant who need to understand what the agent did, rather than watching a generic loading indicator.

## Current state and constraints

- The configured provider is Groq with `openai/gpt-oss-120b`. Groq documents that GPT-OSS returns reasoning in a separate response field by default. The application uses Spring AI 2.0.0 and presently captures `reasoningContent` metadata only into `AgentTrace` logs. The actual mapping and chunk behavior of this exact combination must be verified with a focused provider integration check before relying on it.
- `AgentRuntime` owns model turns and wraps tool callbacks; `AgentOrchestrator` emits assistant text through `SseBroadcaster`. The frontend fetch SSE parser discards every event other than `assistant.delta` and `assistant.replace`.
- The database already contains `ai_run_event`, but only coarse run lifecycle events are persisted. The existing live broadcaster has no replay or event IDs. Conversation messages hold the final answer and some tool messages; they are not a complete ordered execution history.
- FE changes must account for existing uncommitted edits in the chat sidebar and related files. No credentials from `.env` belong in the design, event payload, or UI.

## Goals

- Stream the exact reasoning text that Groq supplies for each model turn, without paraphrasing it or merging it into the final answer.
- Display each model turn and tool invocation in chronological order. A tool card shows its name, arguments, running/succeeded/failed status, duration, and returned result. Each card and reasoning block can open and close independently.
- Persist the ordered timeline and restore it on refresh or conversation switch. Reconnect without duplicates or missing events.
- Keep the final answer visually distinct and continue supporting stop, errors, and current chat behavior.
- Scope all timeline reads and live subscriptions to the owner of the run. Redact credentials and other known secrets from reasoning and tool arguments/results before they are persisted or leave the backend. Apart from that narrow masking, preserve provider reasoning verbatim.

## Non-goals and anti-goals

- Do not invent reasoning when the provider omits it. Show an honest unavailable state for that model turn.
- Do not execute domain tools in the browser. Tool execution and authorization remain on the backend; FE observes the calls and results.
- Do not alter the agent's tool choice, permission checks, approval flow, or substantive answer generation to make the timeline look fuller.
- Do not expose API keys, authorization headers, passwords, or cookies through raw tool payloads.
- A history view that silently loses turns, reorders events, or displays a tool as successful before its result arrives is a failure.

## Approaches

1. **Chosen: extend the existing Spring AI path and persist a unified event stream.** Extract reasoning chunks from `ChatResponse`, publish tool lifecycle events from the existing callback wrappers, and store all public timeline events in `ai_run_event` before SSE delivery. Reuse that stream for live rendering and history. This preserves the current agent runtime and avoids duplicating model orchestration. First verify whether Spring AI 2.0.0 exposes Groq reasoning chunks. If it drops the field, replace only the provider stream adapter with one that preserves Groq's reasoning field; keep the event contract and runtime.
2. Build a direct Groq streaming client for all agent model calls. This offers direct access to provider frames but requires taking over tool-call accumulation, retries, usage, and compatibility behavior already handled by Spring AI. It is larger and less suitable as the first implementation.
3. Stream live text and query messages for history. This is smaller but cannot reliably reconstruct intermediate reasoning, pending tool calls, or exact order after refresh; it does not meet the requested history behavior.

## Event contract and backend flow

- Use one append-only public timeline per run. A dedicated event writer allocates a monotonic sequence within the run, persists the event, then broadcasts it with the same sequence as SSE `id`. Do not use the existing manually incremented lifecycle counter once new event types are added. Define a unique `(run_id, sequence)` constraint and ordered index. Persisting failure must be surfaced as a run/stream error rather than silently advertising an event that history cannot recover.
- Versioned JSON event envelope: `version`, `runId`, `sequence`, `type`, `modelTurn`, `itemId`, `timestamp`, and type-specific `data`. Event types cover `run.started`, `reasoning.started`, `reasoning.delta`, `reasoning.completed`, `tool.started`, `tool.completed`, `tool.failed`, `assistant.delta`, `assistant.replace`, and terminal run status. `itemId` links all chunks and lifecycle events for one reasoning block or tool call. Use the existing run ID plus model-step/attempt/call ordinal to make stable IDs. Attempt boundaries stay distinct so retries are visible and cannot append to the wrong block.
- Capture provider reasoning at the model response boundary and forward each chunk immediately, before mapping the response to visible answer text. Empty reasoning is represented explicitly for that turn, never replaced with an inferred summary. Avoid storing the same growing text repeatedly if the provider returns cumulative chunks; normalize to deltas once at the adapter boundary.
- Emit `tool.started` immediately before invoking a callback, then `tool.completed` or `tool.failed` in its `finally`/error path. Include all domain, discovery, conversation search, and result paging tools. Preserve invocation order; concurrent calls are linked by `itemId` rather than assuming completion order.
- Reasoning and tool payloads pass through a single server-side redaction boundary before persistence and delivery. Preserve useful data, including the full result where permitted, but mask known secret keys and bearer/key patterns. This credential masking is the only intentional change to provider reasoning text. Display truncation is a FE concern; stored payloads remain available for expansion. Apply reasonable storage and transport limits with an explicit oversized-result marker if a tool exceeds them, rather than silently dropping data.
- The owner-checked run events endpoint accepts a cursor (`afterSequence`) and replays persisted events in sequence, then continues live delivery. Subscription registration and replay must have a race-free handoff; clients deduplicate by `(runId, sequence)` when reconnecting. A terminal run replays through its terminal event and closes. Keep bearer-token authentication in fetch, rather than putting it in the URL.

## Frontend behavior

- Build timeline state from event envelopes, keyed by run and item IDs. Do not append arbitrary SSE data into assistant text. Render reasoning blocks and tool cards between user message and final assistant answer in event order. Reasoning blocks stream text while open; users can collapse them at any time. Tool cards show a spinner until a completion/failure event arrives, then reveal arguments and result on expansion.
- For an active run, connect with the latest applied sequence and reconnect after transient failures. On page refresh or conversation switch, load the run timeline from the same owner-checked endpoint. Existing message history supplies user messages and final assistant answers; timeline events supply the intermediate blocks. Avoid displaying the streamed final answer twice when persisted messages arrive.
- Keep scrolling pinned only when the user is already near the bottom; preserve their reading position while they inspect an expanded block. Large blocks scroll within their card. Clearly show cancelled and failed runs, including unfinished tool states.
- If no provider reasoning exists, label the turn `Reasoning không được provider trả về` and show tools/final answer normally.
- Runs created before this feature have no recoverable reasoning timeline. Keep their existing messages and available tool cards, and state that detailed execution history is unavailable for older runs. Do not fabricate historical reasoning or infer tool order from partial data.

## Verification and remaining builder choices

- A focused Groq/Spring AI check must prove whether `openai/gpt-oss-120b` reasoning arrives incrementally through `ChatResponse` and identify the correct metadata/part field. Do not claim raw reasoning support merely because trace capture code exists.
- Verify event ordering, concurrent tool IDs, retries, redaction, cancel/failure, replay handoff, reconnection deduplication, owner isolation, and history after refresh. Verify the FE display with a multi-turn run containing at least one tool call and a long reasoning block.
- The builder may choose component boundaries, cache/query library details, and the exact visual styling within the current Ant Design chat panel. Those choices must retain the interactions and event semantics above.

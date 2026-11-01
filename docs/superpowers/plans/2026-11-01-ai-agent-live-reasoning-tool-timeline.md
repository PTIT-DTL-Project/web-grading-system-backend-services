# AI Agent Live Reasoning and Tool Timeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show the provider's reasoning and every tool invocation live in the chat, with expandable details and a complete timeline after refresh.

**Architecture:** Keep `AgentRuntime` and Spring AI as the agent engine. Normalize model and tool activity into a versioned, durable per-run event log, then use the same owner-checked stream for replay and live SSE. The frontend reduces events into ordered cards and renders final assistant messages separately.

**Tech Stack:** Java 21, Spring Boot 4.1, Spring AI 2.0, PostgreSQL/Flyway, React 19, TypeScript 6, Ant Design 6, fetch SSE.

**Spec:** `docs/superpowers/specs/2026-11-01-ai-agent-live-reasoning-tool-timeline-design.md`

## Global Constraints

- Configured provider/model: Groq `openai/gpt-oss-120b`; never copy secrets from `.env` into tests, logs, events, or commits.
- Keep backend ownership checks, tool authorization, approvals, cancellation, and final-answer semantics.
- Reasoning is provider text in order, except masking credentials before storage/delivery; never synthesize missing reasoning.
- Preserve the user's existing uncommitted frontend edits. Work in the current checkout carefully or isolate only after checking how to retain those edits.
- Old runs cannot have reconstructed reasoning. Show the available history and an explicit legacy-history notice.

## Review Focus

- An SSE subscriber joins while an event is being committed: replay plus live handoff delivers it exactly once; Task 4 tests this.
- Provider sends cumulative rather than incremental reasoning chunks: UI shows the exact text once; Task 1 tests delta normalization.
- Two tools finish out of call order: cards retain start order and correct results; Task 3 tests correlation.
- Reasoning or tool output contains a token/password: persisted and streamed copies are masked; Task 2 tests this.
- User switches conversations, then an old run emits a chunk: the active panel does not show it; Task 6 tests this.

---

## File map

Backend paths below are relative to `ai-assistant-service/`.

- `src/main/java/.../orchestrator/RunTimelineService.java`: append, redact, sequence, and publish public events.
- `src/main/java/.../orchestrator/RunTimelineEvent.java`: versioned event envelope.
- `src/main/java/.../orchestrator/ReasoningChunkNormalizer.java`: normalize provider reasoning chunks into exact deltas.
- `src/main/java/.../orchestrator/AgentRuntime.java`, `.../service/LlmClient.java`: reason and tool lifecycle hooks.
- `src/main/java/.../controller/RunController.java`, `.../orchestrator/SseBroadcaster.java`, `.../repository/RunEventRepository.java`: owner-checked replay/live endpoint.
- `src/main/resources/db/migration/V8__2026-11-01__run_timeline_sequence.sql`: per-run uniqueness and sequence allocation.

Frontend paths below are relative to `../web-grading-system-fe/web-grading-system-fe/`.

- `src/shared/types/aiAgent.ts`, `src/shared/api/endpoints/ai.ts`: typed event protocol and SSE cursor/reconnect.
- `src/features/ai-chat/hooks/runTimelineReducer.ts`, `useRunTimeline.ts`: event reduction and per-run lifecycle.
- `src/features/ai-chat/components/ReasoningCard.tsx`, `ToolExecutionCard.tsx`, `AiChatSidebar.tsx`: expandable ordered rendering and legacy notice.
- Co-located `*.test.ts(x)` files: focused protocol, reducer, and component behavior (add Vitest/Testing Library in the first FE task).

### Task 1: Confirm and extract provider reasoning

**Files:** Modify `LlmClient.java`, `AgentTrace.java`; create `ReasoningChunkNormalizer.java`; test `ReasoningChunkNormalizerTest.java`, `LlmClientTest.java`.

**Interfaces:** `LlmClient.streamAgentTurn(..., Consumer<String> onReasoningDelta)` keeps its current answer-text `Flux<String>` result. `ReasoningChunkNormalizer.accept(String providerChunk): String` returns only newly received reasoning.

- [ ] **Step 1: Write failing tests** for incremental and cumulative `reasoningContent`, absent reasoning, tool-call-only response, and one model turn ending in error; assert concatenated callback text equals provider reasoning exactly once and answer flux excludes it.
- [ ] **Step 2: Run** `./mvnw -Dtest=ReasoningChunkNormalizerTest,LlmClientTest test`; expect the new tests to fail.
- [ ] **Step 3: Implement** the callback and normalizer at the `ChatResponse` boundary; preserve `AgentTrace` capture and keep retry attempts separate. Verify which Spring AI field actually receives Groq's `reasoning` chunks with one credential-safe live provider probe. If Spring AI 2.0 drops them, add a narrowly scoped Groq stream adapter behind the same callback and answer-flux interface; do not replace `AgentRuntime`.
- [ ] **Step 4: Rerun** the two tests and the provider probe; expect exact reasoning bytes and no reasoning mixed into final answer. Record probe outcome in the task commit message.
- [ ] **Step 5: Commit** the extraction and tests.

### Task 2: Persist a versioned, secret-masked run event

**Files:** Create `RunTimelineEvent.java`, `RunTimelineService.java`, migration `V8__2026-11-01__run_timeline_sequence.sql`; modify `RunEventRepository.java`, `AgentOrchestrator.java`; test `RunTimelineServiceTest.java`.

**Interfaces:** `RunTimelineService.append(UUID runId, String type, Integer modelTurn, String itemId, JsonNode data): RunTimelineEvent` persists before publishing. Envelope fields: `version=1`, `runId`, `sequence`, `type`, `modelTurn`, `itemId`, `timestamp`, `data`. `RunTimelineService.listAfter(UUID runId, int sequence): List<RunTimelineEvent>` reads in sequence order.

- [ ] **Step 1: Write failing tests** for concurrent appends allocating unique ascending per-run sequences, persistence preceding broadcast, credential masking in reasoning and nested tool JSON, and explicit failure when persistence fails.
- [ ] **Step 2: Run** `./mvnw -Dtest=RunTimelineServiceTest test`; expect failure.
- [ ] **Step 3: Implement** the event writer with a locked per-run sequence allocation, JSON payload, and a single redaction boundary. Migration adds the counter/unique key and backfills from existing `ai_run_event` rows. Route existing lifecycle/assistant events through it; remove the manually incremented sequence and silent persistence catch. Do not rewrite old event payloads as if they contained reasoning.
- [ ] **Step 4: Run** the focused test and Flyway integration test; expect pass and migration success against an existing run with events.
- [ ] **Step 5: Commit** the event writer and migration.

### Task 3: Emit ordered reasoning and tool lifecycle events

**Files:** Modify `AgentRuntime.java`, `AgentOrchestrator.java`; test `AgentRuntimeTest.java`, `AgentOrchestratorTest.java`.

**Interfaces:** `AgentRuntime.execute(...)` gains `Consumer<ReasoningEvent>` and `Consumer<ToolLifecycleEvent>` hooks; each supplies `modelTurn`, stable `itemId`, and event data. The orchestrator passes both to `RunTimelineService.append`.

- [ ] **Step 1: Write failing tests** for two reasoning turns around a tool, two concurrent tools completing in reverse order, retry attempt separation, tool exception, cancellation during a tool, and no provider reasoning; assert event order and stable IDs.
- [ ] **Step 2: Run** `./mvnw -Dtest=AgentRuntimeTest,AgentOrchestratorTest test`; expect failure.
- [ ] **Step 3: Implement** `reasoning.started/delta/completed` around each model attempt and `tool.started/completed/failed` in the common callback wrapper used by domain, discovery, conversation, and paging tools. Include arguments, output/error, duration and status. Preserve existing `onResult` behavior and final answer path. Flush reasoning before the next tool/terminal event.
- [ ] **Step 4: Rerun** focused tests; expect pass. Inspect one run trace against persisted event order.
- [ ] **Step 5: Commit** the runtime instrumentation.

### Task 4: Replay and resume the owner-checked event stream

**Files:** Modify `RunController.java`, `SseBroadcaster.java`, `RunEventRepository.java`; test `RunEventsSecurityTest.java` and new `RunEventsReplayTest.java`.

**Interfaces:** `GET /api/v1/ai/runs/{id}/events?afterSequence=N` replays all events with `sequence>N`, then delivers live events with SSE `id=sequence` and `event=type`. Terminal runs replay through terminal event and close.

- [ ] **Step 1: Write failing tests** for owner isolation, historical replay, mid-subscription commit race, terminal run close, and an invalid/negative cursor.
- [ ] **Step 2: Run** `./mvnw -Dtest=RunEventsSecurityTest,RunEventsReplayTest test`; expect failure.
- [ ] **Step 3: Implement** register-before-replay with buffering or another race-free handoff; deduplicate by sequence, keep events ordered, and retain bearer-token fetch compatibility. Avoid a live-only path that misses events between run creation and subscription.
- [ ] **Step 4: Rerun** focused tests; expect pass.
- [ ] **Step 5: Commit** replay/resume.

### Task 5: Typed FE protocol and timeline state

**Files:** Modify `src/shared/types/aiAgent.ts`, `src/shared/api/endpoints/ai.ts`, `package.json`; create `src/features/ai-chat/hooks/runTimelineReducer.ts`, `useRunTimeline.ts` and focused tests.

**Interfaces:** `subscribeRunEvents(runId, afterSequence, onEvent, onError): () => void` parses every version-1 envelope and reconnects from last applied sequence. `reduceRunTimeline(state, event): state` builds ordered reasoning/tool/assistant items keyed by `itemId` and deduplicates by `(runId, sequence)`.

- [ ] **Step 1: Add Vitest/Testing Library and write failing tests** for split SSE frames, multiline data, duplicate/reordered IDs, cumulative reasoning normalized upstream, reconnect from cursor, terminal event, and the five Review Focus conditions owned by FE state.
- [ ] **Step 2: Run** `npm test -- --run` in the FE project; expect the new tests to fail.
- [ ] **Step 3: Implement** typed parser/reducer/hook. Keep the token in the Authorization header; abort subscriptions when run/conversation changes; use cursor-based replay on reconnect. Merge persisted message history with run timelines without double-rendering final answer.
- [ ] **Step 4: Run** `npm test -- --run` and `npm run build`; expect pass.
- [ ] **Step 5: Commit** FE protocol/state changes, excluding unrelated pre-existing edits.

### Task 6: Expandable live timeline and history UI

**Files:** Create `ReasoningCard.tsx`; modify `ToolExecutionCard.tsx`, `AiChatSidebar.tsx`, locale files as needed; create component tests.

**Interfaces:** `ReasoningCard` receives `{text, status, modelTurn}`; `ToolExecutionCard` receives tool lifecycle state with `{name, arguments, result, status, durationMs}`. Both own independent expand/collapse state.

- [ ] **Step 1: Write failing UI tests** for live reasoning growth, independently collapsing cards, running/succeeded/failed tool display, history after remount, legacy-run notice, missing reasoning notice, stop/cancel, and conversation switch isolation.
- [ ] **Step 2: Run** `npm test -- --run`; expect failure.
- [ ] **Step 3: Implement** ordered card rendering in the existing sidebar, with a distinct final answer, in-card scrolling for long text, and bottom-follow only while the user remains near bottom. Preserve current send/stop behavior and existing local FE edits.
- [ ] **Step 4: Run** `npm test -- --run`, `npm run build`, `npm run lint`, backend `./mvnw test`; expect pass. Manually exercise a Groq multi-turn/tool run, refresh, reconnect, cancel, and expand/collapse in the browser.
- [ ] **Step 5: Commit** UI changes and document any provider-specific limitation found by the live check.

## Self-review

The tasks cover the spec's provider extraction, persistence, ordering, redaction, replay, reconnection, UI, and legacy behavior. Cross-task contracts use the same envelope and `itemId`/sequence fields. The live provider probe is a gate: if reasoning is absent through Spring AI, the fallback changes only the adapter in Task 1 and leaves Tasks 2–6 intact.

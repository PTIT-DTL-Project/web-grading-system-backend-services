# AI Agent Tool Discovery and Context Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send a small, expandable tool set and bounded, complete conversation context to the AI agent while retaining full access to earlier decisions and oversized tool results.

**Architecture:** Select initial tools from role-authorized metadata, then use `search_tools` to expand callbacks during a bounded agent continuation. Build each model request through one context preflight that applies separate history and tool budgets plus the overall model budget. Persist complete oversized tool results for authorized page retrieval and summarize only complete older conversation messages.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring AI 2.0.0, Spring Data JPA, PostgreSQL/Flyway, JUnit 5/Mockito.

**Spec:** `docs/superpowers/specs/2026-10-24-ai-agent-tool-discovery-context-design.md`

## Global Constraints

- First request: at most four relevant domain tools plus `search_tools` and `search_conversation`.
- Default recent-history target: ten complete messages; select by message count, never by a per-message token cut.
- Apply separate configurable history and tool token budgets, and the configured overall input, output reserve, and safety margin.
- Search only the current conversation and the current saved role snapshot; enforce ownership.
- Persist complete oversized tool results temporarily; page retrieval must not re-execute a domain tool or bypass write approval.
- Preserve existing REST/SSE contracts, role scoping, and confirmation for domain writes.

## Review Focus

- Empty or ambiguous Vietnamese request: initial selection stays bounded and `search_tools` is usable (Task 1 test).
- Role changes after earlier messages: neither context nor search exposes the prior role scope (Tasks 2 and 3 tests).
- A single user message above the history budget: explicit error, without truncation or provider call (Task 2 test).
- A single large JSON element or Unicode text tool result: exact reconstruction across pages (Task 4 test).
- Tool discovery following a write proposal: no duplicate proposal or domain execution (Task 5 test).

---

## File map

- `tools/ToolRegistry.java`: authorized metadata search and callback resolution by name.
- `tools/ToolSelector.java`, `tools/SearchToolsTool.java`: deterministic initial selection and model-facing discovery.
- `tools/SearchConversationTool.java`, `repository/MessageRepository.java`: bounded, owner-scoped search of original messages.
- `orchestrator/ContextBuilder.java`, `orchestrator/ContextPreflight.java`: complete-message history selection, summaries, and separate budget validation.
- `memory/JpaChatMemory.java`, `repository/ConversationSummaryRepository.java`: bounded reads and role-scoped summary checkpoints.
- `service/ConversationSummarizer.java`: chunked rolling summaries through a bounded LLM request.
- `domain/run/StoredToolResult.java`, `repository/StoredToolResultRepository.java`, `service/ToolResultStore.java`, `tools/GetToolResultPageTool.java`: temporary full-result storage and authorized exact page retrieval.
- `service/LlmClient.java`, `orchestrator/AgentOrchestrator.java`: model continuation with dynamic callbacks and preflight on every request.
- `config/AgentProperties.java`, `application.yaml`, Flyway V5–V7 migrations: limits, retention, and append-only schema changes.

### Task 1: Capped initial tools and authorized discovery

**Files:**
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolSelector.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/SearchToolsTool.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolRegistry.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/config/AgentProperties.java`
- Modify: `ai-assistant-service/src/main/resources/application.yaml`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolSelectorTest.java`

**Interfaces:**
- Produces: `ToolSelector.selectInitial(String userText, Set<String> roles, int limit): List<String>`; `ToolRegistry.searchAuthorized(String query, Set<String> roles, int limit): List<ToolDefinition>`; `ToolRegistry.callbacksForNames(Set<String> names, AgentContext context, BiConsumer<AgentTool,ToolResult> onResult): List<ToolCallback>`.
- `SearchToolsTool` accepts `{query:string}` and reports only authorized matching tool metadata. It records discovered names in a per-run selection object supplied by orchestration; it never invokes a discovered domain tool.

- [x] **Step 1: Write failing tests** for capped deterministic Vietnamese selection, zero-match selection, role filtering, and discovering an additional tool after the initial selection.
- [x] **Step 2: Run** `mvn -Dtest=ToolSelectorTest,ToolRegistryTest test` in `ai-assistant-service`; confirm new assertions fail.
- [x] **Step 3: Implement** the interfaces above, with stable metadata scoring and a small explicit Vietnamese synonym table; add configurable `initialToolLimit=4`, `toolSearchLimit=5`.
- [x] **Step 4: Rerun** the focused tests; expect zero failures.
- [x] **Step 5: Commit** only Task 1 code and tests.

### Task 2: Complete-message context and rolling summary

**Files:**
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextPreflight.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/ConversationSummarizer.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilder.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/memory/JpaChatMemory.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/MessageRepository.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/ConversationSummaryRepository.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/conversation/ConversationSummary.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/config/AgentProperties.java`
- Modify: `ai-assistant-service/src/main/resources/application.yaml`
- Create: `ai-assistant-service/src/main/resources/db/migration/V5__2026-10-24__role_scoped_summaries.sql`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextPreflightTest.java`

**Interfaces:**
- Produces: `ContextPreflight.prepare(UUID conversationId, UUID ownerId, Set<String> roles, String systemPrompt, List<ToolCallback> tools, List<Message> inFlight): PreparedContext`; `PreparedContext` holds complete chronological Spring AI messages plus summary and budget totals.
- `ConversationSummarizer.summarize(String priorSummary, List<Message> completeMessages, int maxSummaryTokens): String` processes bounded chunks. Repository queries filter by conversation ID and normalized saved run roles; summary lookup uses the same role scope and `throughMessageId` checkpoint.

- [x] **Step 1: Write failing tests** for ten-message selection, intact messages, separate history/tool/overall budget checks, whole-message eviction and summary before prompt construction, role changes, failed summary, and oversized newest message.
- [x] **Step 2: Run** `mvn -Dtest=ContextPreflightTest,JpaChatMemoryTest test`; confirm new assertions fail.
- [x] **Step 3: Implement** bounded reverse-page message reads, role-scoped summary storage via V5 migration, and preflight. Set `historyMaxTokens=12000` and `toolMaxTokens=6000` under the existing 26000-token configured maximum and reserves. Keep original messages untouched; never call the unbounded `JpaChatMemory.get()` on the agent path.
- [x] **Step 4: Rerun** focused tests; expect zero failures.
- [x] **Step 5: Commit** Task 2 files.

### Task 3: Search old decisions in the current conversation

**Files:**
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/SearchConversationTool.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/MessageRepository.java`
- Create: `ai-assistant-service/src/main/resources/db/migration/V6__2026-10-24__conversation_search_index.sql`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/SearchConversationToolTest.java`

**Interfaces:**
- Produces: `search_conversation({query:string,cursor?:string})` returning a bounded page of original message ID, role, sequence number, and excerpt. Query always uses `AgentContext.conversationId`, verifies the conversation owner against `AgentContext.userId`, and applies current run role scope.

- [x] **Step 1: Write failing tests** for retrieval of an old decision, other-conversation and changed-role exclusion, invalid owner, empty query, and bounded pagination.
- [x] **Step 2: Run** `mvn -Dtest=SearchConversationToolTest test`; confirm new assertions fail.
- [x] **Step 3: Implement** the scoped repository search, cursor, and callback. Add a PostgreSQL text-search index in V6 if the chosen query needs one; keep tests compatible with H2.
- [x] **Step 4: Rerun** focused tests; expect zero failures.
- [x] **Step 5: Commit** Task 3 files.

### Task 4: Preserve and page oversized tool results

**Files:**
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/run/StoredToolResult.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/StoredToolResultRepository.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/ToolResultStore.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/GetToolResultPageTool.java`
- Create: `ai-assistant-service/src/main/resources/db/migration/V7__2026-10-24__stored_tool_results.sql`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/config/AgentProperties.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/ToolResultStoreTest.java`

**Interfaces:**
- Produces: `ToolResultStore.offer(String fullResult, AgentContext context, int availableToolTokens): ToolResultDelivery` and `ToolResultStore.page(UUID storedId, String cursor, AgentContext context, int availableToolTokens): ToolResultPage`; `get_tool_result_page` delegates to `page`.
- Config: `historyMaxTokens=12000`, `toolMaxTokens=6000`, and `storedToolResultTtlMinutes=60` under the existing overall limit. Document them in YAML.

- [x] **Step 1: Write failing tests** for whole fitting result, exact multi-page reconstruction of 100 JSON records and long Unicode text, oversized single element, expiry, owner/run/role isolation, and no domain re-execution.
- [x] **Step 2: Run** `mvn -Dtest=ToolResultStoreTest test`; confirm new assertions fail.
- [x] **Step 3: Implement** full-result persistence, exact page boundaries, cursor validation, and bounded cleanup. Use `ai_tool_execution` only for redacted audit data.
- [x] **Step 4: Rerun** focused tests; expect zero failures.
- [x] **Step 5: Commit** Task 4 files.

### Task 5: Wire dynamic continuation and preflight into the run

**Files:**
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentOrchestrator.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/LlmClient.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilder.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentOrchestratorTest.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/LlmClientTest.java`

**Interfaces:**
- Consumes: `ToolSelector.selectInitial`, `ToolRegistry.callbacksForNames`, `ContextPreflight.prepare`, `ToolResultStore.offer`, and the three model-facing utility tools from Tasks 1, 3, and 4.
- Produces: one bounded model continuation that rebuilds callbacks and context before each provider request. A `search_tools` result updates the selected-name set for the next request; tool outputs enter the tool budget through full delivery or stored pages. Preserve the existing run limits, SSE events, reply persistence, and write approval.

- [x] **Step 1: Write failing tests** for the first callback cap, discovering and calling another tool after an intermediate call, second discovery in one run, preflight on every model request, full fitting tool output, and exactly one pending write proposal.
- [x] **Step 2: Run** `mvn -Dtest=AgentOrchestratorTest,LlmClientTest test`; confirm new assertions fail.
- [x] **Step 3: Implement** a controlled model/tool continuation using the local Spring AI 2.0 API so callback changes take effect on the next request. Do not depend on a fixed callback list inside one `ChatClient` stream; do not emit an intermediate final answer as the run's reply.
- [x] **Step 4: Rerun** focused tests; expect zero failures.
- [x] **Step 5: Run** `mvn test` in `ai-assistant-service`; expect the whole module suite to pass, and inspect one multi-step trace with a second discovery.
- [x] **Step 6: Commit** Task 5 files and any completed spec/plan documentation.

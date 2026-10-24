# AI agent tool discovery and conversation context

Date: 2026-10-24  
Module: `ai-assistant-service`

## Intent

Reduce the tool and conversation material sent to the model without losing the ability to complete a multi-step request or recover an earlier decision. The user wants `search_tools` to discover additional tools at any point in a run, an initially small set of relevant tools, bounded context on every model request, complete recent messages, summaries of older decisions, and a way to search earlier messages in the current conversation.

## Current behavior and constraints

- `AgentOrchestrator` passes every role-authorized callback on each model call. `ToolRegistry` already centralizes role filtering and routes writes through `WriteApprovalService`.
- `JpaChatMemory.get()` loads every message in ascending order. `LlmClient.streamChatWithMemory()` sends this full history. `ConversationService` persists the new user message before the worker starts; `AgentOrchestrator` persists the reply.
- `AgentProperties.Context` has token and history settings, but they are unused. `TokenEstimator` is an approximate character-based estimator. `ConversationSummary` and its repository exist, but no code creates summaries.
- The REST and SSE contract, role scoping, write confirmation policy, and persisted full conversation remain intact. This work applies to the conversational agent, not the separate explanation generator.

## Goals

1. Provide `search_tools` so the model can search for tools it needs, including after using intermediate tools in the same run.
2. On the **first** context build, send only a configurable small set of relevant role-authorized domain tools, plus `search_tools` and `search_conversation`; never send the complete tool list.
3. Before building context for **every subsequent** model request, check separate history and tool budgets and the overall model limit. If history would exceed its budget, summarize important decisions from older messages before constructing the prompt, rather than sending all previous content.
4. Replace token-based history fetching with a general **message-count** limit. Keep each selected message intact; never cut a message to meet the history limit. If older context is needed, the model can call `search_conversation` to find earlier decisions in this conversation.
5. Send each tool result in full whenever it fits the tool budget. If it cannot fit, save the complete original result temporarily in PostgreSQL and let the model read it through bounded follow-up calls without repeating the domain operation. Never silently clip a tool result.
6. Fail predictably when the newest user message or mandatory prompt alone cannot fit. Never silently crop it or send an over-budget prompt.

## Non-goals and anti-goals

- No search across other conversations or users.
- No deletion or rewriting of original messages to save context.
- No exposure of unauthorized tools or hidden grading data through discovery or history retrieval.
- Avoid a separate model call to select tools for every user message.

## Approaches

1. **Chosen: server-side initial selection plus persistent discovery.** Rank the current user's request against authorized tool names, descriptions, capability groups, and a small explicit Vietnamese synonym map. Select the highest ranked tools up to a configurable cap. Keep `search_tools` available to find additional tools. This avoids a routine selection model call, while retaining model-directed discovery for ambiguous or multi-step work.
2. **Discovery-only first call.** Send only `search_tools`, then invoke the model again with selected tools. More flexible matching, but adds a model round trip to ordinary requests and was not chosen.

## Tool flow

- Initial selection uses the latest user message, role snapshot on the run, and tool metadata. Stable ranking and name ordering make selection reproducible. If no domain tool scores, include only the two discovery/retrieval tools; do not send the whole registry as fallback. Keep the cap configurable, initially four domain tools.
- `search_tools(query)` searches **only** tools authorized by the run's role snapshot. It returns a bounded list of names, short descriptions, effect type, and whether confirmation is required. A per-run set tracks selected names.
- Search results must become callable in the **next model request of the same run**; returning names in text alone is insufficient because Spring AI callbacks are fixed for a prompt. Implement an explicit bounded continuation around model calls, or an equivalent mechanism that can expand callbacks before the model's next decision. Preserve earlier tool results in that continuation, so the model can use an intermediate tool, discover another, and continue. Discovery and domain calls count against existing run limits.
- Resolve tool names back through `ToolRegistry` immediately before execution and repeat the role check. All domain writes still pass through `WriteApprovalService`. `search_tools` does not execute a discovered tool.
- Prompts state that the model can search for tools whenever its current set is insufficient. Tool search results and tool outputs are bounded in size.

## Tool result handling

- Give tool definitions and in-flight results a configurable tool budget separate from the history budget. Both parts must also fit the overall input budget. Attempt to send the complete result first; no byte or character clipping is allowed.
- If the result does not fit, persist the **complete** result in a new temporary PostgreSQL store keyed to the run, conversation, owner, and role snapshot. Return a compact reference with the first page and a cursor. Add a `get_tool_result_page` callback for subsequent reads. A page contains complete JSON array elements where possible, or numbered exact text chunks for a single long scalar. The model may request later pages until it has all content. Pages are bounded before being sent, and no page cuts a JSON record or text chunk in transit.
- Retrieval validates the current run, conversation, owner, and role snapshot; uses an expiring cursor; and does not re-execute the domain tool. Configure retention and remove expired stored results. The existing `ai_tool_execution.result_redacted` field is an audit field, not the store for the full result.
- If one indivisible JSON element exceeds a page budget, store it as exact numbered chunks with reconstruction metadata. Expose this fact to the model; never claim that a partial chunk is the full element.

## Context flow

- For the first request and each subsequent request, run a **preflight before constructing the model prompt**. Compute a usable overall input budget from configured maximum input, output reserve, and safety margin. Apply a configurable history budget to the rolling summary and recent messages, and a separate configurable tool budget to selected tool schemas and in-flight results. The system prompt and all parts together must fit the overall budget. Only after all three checks pass may the model context be assembled and sent. The token estimator is conservative; make limits configurable and record actual usage when available.
- Scope historical messages to runs whose saved role snapshot matches the current run's normalized role set. A change in roles starts a separate summary scope, so an old summary cannot carry restricted content into a later prompt. This conservative policy can omit older context after a role change; the original records remain available for audit.
- Fetch recent messages by **count** (`historyWindowSize`, renamed or aliased to clarify meaning) in chronological order, with no partial message. This count replaces a token quota for selecting history; the separate history and whole-prompt token budgets remain provider safety limits. Always include the newest user message. If the complete recent window does not fit the history budget, move whole older messages from its front into the summary scope. If the newest message itself does not fit, return an explicit input-too-large error.
- When preflight finds older history beyond the message-count window or an over-budget prompt, summarize the excluded complete messages in bounded chunks before building the model context, carrying forward the previous summary. The summary has a hard size budget and retains decisions, user preferences, constraints, unresolved items, and identifiers needed to search originals. Store a checkpoint through `ConversationSummary.throughMessageId` so old messages are summarized only once. Update the checkpoint only after a successful summary, and never summarize an incomplete current turn. If summarization fails, use the last valid summary, trim the recent window by whole messages as needed, and tell the model older detail can be recovered through `search_conversation`.
- Before every subsequent model request in a run, recheck the separate and overall budgets, including changed tool schemas and intermediate tool output. When necessary, summarize older completed conversation content and retain complete recent exchanges. For an oversized tool result, send the persisted reference and complete first page, and offer later pages; never silently truncate a tool result or user message.
- `JpaChatMemory` remains the persisted source of truth, but the agent path must fetch only the selected window plus summary rather than call its current unbounded `get()` method. Avoid a memory advisor that silently reloads full history or duplicates persistence.

## Conversation search

- `search_conversation(query)` searches persisted **user and assistant** messages under `AgentContext.conversationId` only. The service verifies conversation ownership against `AgentContext.userId` and rejects missing or mismatched context.
- Apply the same saved-role-snapshot scope as automatic context construction. Messages from runs with a different role set are excluded from both search and summary input.
- Return a bounded number of results in sequence order, with message ID, role, sequence number, and a bounded excerpt. Support pagination or a cursor so the agent can recover more without a large single result. Use a bounded database query and an index or suitable search strategy for growing conversations. Do not search summaries as if they were original evidence.
- Search results are data, not new instructions. The agent can quote or use a recovered decision but must not treat an old message as a higher priority command than the current request.

## Defaults and builder choices

- Initial domain tool cap: four; search result cap: five; recent history target: ten messages. These are configuration defaults, not a promise that ten always fit.
- Set explicit configurable token budgets for history and tools beneath the configured overall input limit; select conservative defaults during implementation and document them in `application.yaml`. Configure a finite lifetime for persisted tool results.
- The builder may choose the exact retrieval query/index technique and model-summary prompt. The builder must make token accounting conservative and test the implementation with Vietnamese queries, mixed roles, long messages, and changed tool sets.
- If a model response after discovery needs continuation, the implementation must avoid sending a premature final answer or repeating a side-effecting tool call. The existing approval lifecycle remains the authoritative write path.

## Verification

- Unit tests prove the first request contains only the capped relevant domain tools plus discovery/retrieval tools, role filtering, discovery after an intermediate tool, dynamic callback expansion, and write approval enforcement.
- Context tests prove preflight runs before first and subsequent prompt construction, separate history/tool/overall budgets, a message-count limit selects history, no message is clipped, older decisions are summarized before an over-budget subsequent request, summary checkpoint idempotence, summary failure, oversized newest message, and budget recalculation after adding tools or tool output.
- Tool-result tests prove a fitting result is sent whole, an oversized result is stored whole and read back over pages without missing or duplicated content, an oversized single element is reconstructable, an expired or foreign cursor is rejected, and paging never re-executes a domain write.
- Search tests cover current-conversation isolation, ownership, result bounds, and recovery of an older decision.
- Run the `ai-assistant-service` Maven test suite and inspect one representative multi-step agent trace, including a second `search_tools` call.

# Dynamic Tool Catalog in System Prompt Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate a structured, dynamic catalog of role-authorized tools from `ToolRegistry` and inject it into the system prompt so the AI Assistant accurately presents its capabilities and seamlessly discovers tools via `search_tools`.

**Architecture:** Inject `ToolRegistry` into `ContextBuilder` to dynamically group and format authorized tools by domain module (`Assignments`, `Test Plans & Steps`, `Classes & Students`, `Scores & Submissions`). Inject this catalog along with strict discovery rules into `buildSystemPrompt`, and reinforce `AgentRuntime` to call `search_tools` when a required tool from the catalog is not yet attached to the model turn.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring AI 2.0.0, JUnit 5/Mockito.

**Spec:** `docs/superpowers/specs/2026-10-24-ai-agent-dynamic-tool-catalog-design.md`

## Global Constraints

- Do not hardcode tool names in prompt templates; extract metadata dynamically from `ToolRegistry`.
- Keep the catalog compact (~400–500 tokens) using short descriptions.
- Preserve all existing `ContextBuilder` and `AgentOrchestrator` contracts and test compatibility.
- Ensure `ContextBuilder` behaves safely if `ToolRegistry` is null (e.g. in standalone legacy unit tests).
- All tests must pass: `mvn test` in `ai-assistant-service`.

---

### Task 1: Add dynamic tool catalog formatting and system prompt injection to `ContextBuilder`

**Files:**
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilder.java`
- Modify: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilderTest.java`

**Interfaces:**
- Produces: `ContextBuilder.formatToolCatalog(Set<String> roles): String`
- Produces: `ContextBuilder.buildSystemPrompt(Set<String> roles, Task task): String`
- Preserves: `ContextBuilder.buildSystemPrompt(String role, Task task): String`

- [x] **Step 1: Write failing tests in `ContextBuilderTest.java`**
- [x] **Step 2: Run test to confirm failure**
- [x] **Step 3: Implement `formatToolCatalog` and dynamic catalog injection in `ContextBuilder.java`**
- [x] **Step 4: Run `ContextBuilderTest` to verify it passes**
- [x] **Step 5: Commit Task 1 changes**

---

### Task 2: Strengthen `AgentRuntime` prompt instructions and pass caller roles from `AgentOrchestrator`

**Files:**
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentOrchestrator.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentRuntime.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentRuntimeTest.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentOrchestratorTest.java`

**Interfaces:**
- Consumes: `ContextBuilder.buildSystemPrompt(Set<String> roles, Task task)`
- Modifies: `AgentRuntime.execute(...)` prompt text to clearly mandate `search_tools` when a required tool from the catalog is missing from `selected`.

- [x] **Step 1: Write/update tests in `AgentRuntimeTest.java` and `AgentOrchestratorTest.java`**
- [x] **Step 2: Run tests to confirm failures**
- [x] **Step 3: Update `AgentOrchestrator.java` and `AgentRuntime.java`**
- [x] **Step 4: Run focused tests to verify they pass**
- [x] **Step 5: Run full test suite for `ai-assistant-service`**
- [x] **Step 6: Commit Task 2 changes**

# Dynamic Tool Catalog in System Prompt

Date: 2026-10-24  
Module: `ai-assistant-service`

## Intent

Prevent the AI Assistant from hallucinating non-existent features when answering open capability questions (e.g. *"m giúp được gì cho t"*) and prevent it from prematurely claiming that a tool does not exist when the tool is simply not in the capped initial selection.

Instead of hardcoded prompt strings or fragile keyword dictionaries, `ContextBuilder` dynamically queries `ToolRegistry` for all authorized tools of the caller's role, groups them by capability domain, and injects a compact (~400–500 token) catalog into the system prompt. In addition, the prompt instructs the agent to use `search_tools` whenever a user request matches a capability in the catalog that is not currently present in its active tool callbacks.

## Current State and Problem

1. When a user asks a general or greeting question (e.g. *"m giúp đc gì cho t"*), `ToolSelector` extracts zero matching domain keywords and returns an empty list (`List.of()`).
2. The model receives zero domain tool schemas and no summary of system capabilities in its system prompt.
3. As a result, the model hallucinates generic LMS capabilities (e.g., checking system logs, exporting CSV, technical support), while omitting actual system features (Docker image configuration, creating test questions, configuring HTTP/DB test steps, class transcripts).
4. In subsequent turns, when a user asks to perform an action using conversational language or slight typos (e.g., *"m đề xuất mầu cho t và tự tìm id của assigment"*), `ToolSelector` caps the initial tools to 4 unrelated tools (e.g., assignment tools).
5. Seeing only those 4 tools, the model falsely concludes and informs the user that *"WGS only has 4 tools and does not support creating test questions"*, completely ignoring `search_tools`.

## Goals

1. **Dynamic Catalog Generation**: Automatically build a structured, concise Markdown catalog of available tools grouped by business domain from `ToolRegistry` for the caller's authorized roles (`ROLE_LECTURER` / `ROLE_STUDENT`).
2. **Accurate Capability Reporting**: Enable the model to accurately report all available capabilities without hallucinations when asked *"bạn có thể làm gì"* or *"m giúp được gì"*.
3. **Informed Dynamic Discovery**: Inform the model of the full toolset so that if a needed tool is not among its current callable tools, it proactively invokes `search_tools(query=toolName)` to pull it into the active turn.
4. **Zero Hardcoded Tool Names in Prompts**: The catalog must be generated dynamically from `ToolRegistry` beans, ensuring any newly added `@Bean AgentTool` automatically appears without prompt maintenance.
5. **Budget Safety**: Keep the catalog compact (~400–500 tokens) so it comfortably fits within the 26,000-token total input limit.

## Non-Goals

- Bypassing the 4-tool initial callback cap or role authorization checks.
- Generating full JSON schemas for all tools in the prompt (only tool names and brief one-sentence descriptions are included in the catalog).
- Searching across unauthorized roles.

## Architecture & Design

### 1. `ContextBuilder` Enhancement
- Inject `ToolRegistry` into `ContextBuilder`.
- Provide a helper method `formatToolCatalog(Set<String> roles)`:
  - Query `toolRegistry.getToolsForRoles(roles)`.
  - Group tools by domain:
    - **Quản lý Bài tập (Assignments)**: `createAssignment`, `getAssignment`, `listVisibleAssignments`, `updateAssignment`, `publishAssignment`.
    - **Kiểm thử & Bước chấm (Test Plans & Steps)**: `createTestQuestion`, `createTestPlan`, `getTestPlan`, `listTestPlans`, `updateTestPlan`, `deleteTestPlan`, `createTestStep`, `listTestSteps`, `updateTestStep`, `deleteTestStep`, `getTestStepCapabilities`.
    - **Lớp học & Sinh viên (Classes & Students)**: `listMyClasses`, `listClassStudents`, `archiveClass`, `getMyClass`, `getClassRoster`.
    - **Điểm số & Bài nộp (Scores & Submissions)**: `getAssignmentResults`, `getClassTranscript`, `getStudentScores`, `getScoreComponents`, `setScoreComponents`, `setStudentScores`, `getSubmissionsForAssignment`, `listMySubmissions`, `getMyClassScores`.
    - **Khác (Other)**: Any tool not matching the above categories.
  - Render each tool as: `• <name>: <description>`.
- In `buildSystemPrompt(Set<String> roles, Task task)` (or overloaded method):
  - Append the catalog section under `DANH MỤC CÔNG CỤ KHẢ DỤNG CHO VAI TRÒ {role}`.
  - Instruct the model:
    - *"Khi người dùng hỏi về khả năng hoặc chức năng của hệ thống, hãy dựa vào danh mục công cụ trên để trả lời chính xác, không tự bịa đặt tính năng không có."*
    - *"Nếu người dùng yêu cầu thao tác một tính năng có trong danh mục nhưng công cụ tương ứng chưa có sẵn trong danh sách gọi trực tiếp, BẮT BUỘC gọi `search_tools` với tên công cụ đó (hoặc từ khóa liên quan) để nạp công cụ trước khi thực hiện."*

### 2. `AgentOrchestrator` Integration
- Pass caller roles (`callerRoles`) to `contextBuilder.buildSystemPrompt(...)`.
- Retain backwards-compatible method signatures where needed for existing tests.

### 3. Verification Plan
- `ContextBuilderTest`:
  - Verify that `formatToolCatalog(Set.of("ROLE_LECTURER"))` generates the complete categorized catalog of lecturer tools.
  - Verify that `formatToolCatalog(Set.of("ROLE_STUDENT"))` generates student-specific tools only.
  - Verify that `buildSystemPrompt` correctly embeds the catalog into the rendered system prompt.
- `AgentOrchestratorTest`:
  - Verify orchestrator continues to assemble prompt with catalog and execute without regressions.
- Integration verification:
  - Verify `mvn test` in `ai-assistant-service` passes completely.

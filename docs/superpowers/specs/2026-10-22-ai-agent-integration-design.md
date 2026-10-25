# AI Agent Integration Design Spec — WGS

> **Date**: 2026-10-22
> **Source**: `WGS_AI_AGENT_ARCHITECTURE.md` + brainstorming session
> **Status**: Approved design, pending implementation plan
> **Scope**: Full agent system — cả 4 giai đoạn, cho cả sinh viên và giảng viên

---

## 1. Tổng quan & Quyết định

### Mục tiêu

Tích hợp AI Agent vào `ai-assistant-service` hiện có. Giữ nguyên tính năng AI Explanation đang chạy. Agent là module mới song song, hỗ trợ:

1. **Sinh viên**: Tra cứu lớp/bài tập, hỏi đáp hệ thống, tìm bài phù hợp, giải thích kết quả chấm
2. **Giảng viên**: Tra cứu dữ liệu, tạo bài tập qua hội thoại, quản lý nghiệp vụ được cấp quyền

### Quyết định chính

| Vấn đề | Lựa chọn |
|--------|----------|
| Framework | Spring Boot 4.1.0 (giữ nguyên) + Spring AI (thêm mới) |
| LLM Provider | OpenAI-compatible API (cấu hình base-url linh hoạt) |
| Điều phối | Một agent, tool theo quyền — vòng lặp do ứng dụng kiểm soát |
| Streaming | SSE (Server-Sent Events) |
| Frontend | Chat sidebar/drawer bên phải (kiểu Copilot) |
| Flow tạo bài | Fully conversational — không draft riêng, không nút Confirm |
| AI Explanation | Giữ nguyên hoàn toàn, không sửa |

---

## 2. Database Schema

### Bảng mới (thêm vào `ai_db`, giữ nguyên `ai_explanations`)

#### `ai_conversation`
```sql
CREATE TABLE ai_conversation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    title VARCHAR(255),
    active_task_id UUID,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_conversation_owner ON ai_conversation(owner_id);
```

#### `ai_message`
```sql
CREATE TABLE ai_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id),
    run_id UUID,
    role VARCHAR(20) NOT NULL, -- 'user', 'assistant', 'tool'
    content TEXT,
    tool_call_id VARCHAR(100),
    tool_name VARCHAR(100),
    tool_arguments JSONB,
    tool_result JSONB,
    client_message_id VARCHAR(100),
    sequence_no INTEGER NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_message_conversation ON ai_message(conversation_id, sequence_no);
CREATE UNIQUE INDEX uq_message_client_id ON ai_message(conversation_id, client_message_id)
    WHERE client_message_id IS NOT NULL;
```

#### `ai_run`
```sql
CREATE TABLE ai_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id),
    status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
    parent_run_id UUID,
    checkpoint_json JSONB,
    version INTEGER NOT NULL DEFAULT 1,
    limits_json JSONB,
    usage_json JSONB,
    error_code VARCHAR(50),
    error_message TEXT,
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_run_conversation ON ai_run(conversation_id);
CREATE INDEX idx_run_status ON ai_run(status) WHERE status IN ('QUEUED', 'RUNNING');
```

Run statuses: `QUEUED`, `RUNNING`, `WAITING_USER`, `WAITING_APPROVAL`, `SUCCEEDED`, `FAILED`, `CANCELLED`, `LIMIT_REACHED`

#### `ai_task`
```sql
CREATE TABLE ai_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id),
    owner_id UUID NOT NULL,
    type VARCHAR(50), -- 'CREATE_ASSIGNMENT', 'QUERY', 'EXPLAIN', etc.
    goal_json JSONB,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    state_json JSONB, -- working state: collectedData, pendingQuestions, etc.
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_task_conversation ON ai_task(conversation_id);
```

#### `ai_conversation_summary`
```sql
CREATE TABLE ai_conversation_summary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id),
    through_message_id UUID NOT NULL,
    summary TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_summary_conversation ON ai_conversation_summary(conversation_id);
```

#### `ai_operation`
```sql
CREATE TABLE ai_operation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID REFERENCES ai_task(id),
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    command_type VARCHAR(50) NOT NULL,
    request_hash VARCHAR(64),
    payload_json JSONB NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    result_ref JSONB,
    attempts INTEGER NOT NULL DEFAULT 0,
    next_check_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
```

Operation statuses: `PENDING`, `IN_PROGRESS`, `SUCCEEDED`, `FAILED`, `RECONCILING`

#### `ai_tool_execution`
```sql
CREATE TABLE ai_tool_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES ai_run(id),
    tool_call_id VARCHAR(100),
    tool_name VARCHAR(100) NOT NULL,
    arguments_redacted JSONB,
    result_redacted JSONB,
    status VARCHAR(20) NOT NULL,
    latency_ms INTEGER,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_tool_exec_run ON ai_tool_execution(run_id);
```

#### `ai_pending_action`
```sql
CREATE TABLE ai_pending_action (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID REFERENCES ai_task(id),
    run_id UUID REFERENCES ai_run(id),
    type VARCHAR(30) NOT NULL, -- 'QUESTION', 'CONFIRM'
    question_json JSONB,
    options_json JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_pending_task ON ai_pending_action(task_id) WHERE status = 'PENDING';
```

#### `ai_run_event`
```sql
CREATE TABLE ai_run_event (
    id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES ai_run(id),
    sequence INTEGER NOT NULL,
    type VARCHAR(50) NOT NULL,
    payload_json JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_run_event_run ON ai_run_event(run_id, sequence);
```

---

## 3. REST API Endpoints

| Method | Endpoint | Purpose | Response |
|--------|----------|---------|----------|
| `POST` | `/api/v1/ai/conversations` | Tạo conversation | `201` + conversationId |
| `GET` | `/api/v1/ai/conversations` | List conversations của user | Paginated list |
| `GET` | `/api/v1/ai/conversations/{id}` | Chi tiết conversation | Conversation + recent messages |
| `DELETE` | `/api/v1/ai/conversations/{id}` | Xóa conversation | `204` |
| `POST` | `/api/v1/ai/conversations/{id}/messages` | Gửi message → tạo run | `202` + runId |
| `GET` | `/api/v1/ai/conversations/{id}/messages` | Lịch sử messages | Paginated list |
| `GET` | `/api/v1/ai/runs/{id}` | Trạng thái run | Run status + result |
| `GET` | `/api/v1/ai/runs/{id}/events` | SSE stream | Event stream |
| `POST` | `/api/v1/ai/runs/{id}/cancel` | Hủy run | `200` |
| `GET` | `/api/v1/ai/operations/{id}` | Tra cứu operation | Operation status + result |

### Request/Response DTOs

**Send Message Request:**
```json
{
  "clientMessageId": "uuid-client-gen",
  "text": "Tạo bài REST API cho lớp A",
  "expectedTaskVersion": 3
}
```

**Send Message Response (202):**
```json
{
  "runId": "uuid-run",
  "conversationId": "uuid-conv",
  "messageId": "uuid-msg"
}
```

### SSE Event Types

| Event | Data |
|-------|------|
| `run.started` | `{ runId }` |
| `tool.started` | `{ toolCallId, toolName }` |
| `tool.completed` | `{ toolCallId, status, summary }` |
| `assistant.delta` | `{ text }` (streaming text chunks) |
| `assistant.options` | `{ question, options[] }` (agent hỏi lại) |
| `task.updated` | `{ taskId, type, status }` |
| `operation.succeeded` | `{ operationId, resourceId, resourceType }` |
| `operation.failed` | `{ operationId, errorCode, message }` |
| `run.completed` | `{ runId, status }` |
| `run.failed` | `{ runId, errorCode, message }` |

---

## 4. Agent Orchestrator

### Agent Loop

```
RunWorker (async) picks up QUEUED run:
  1. Run → RUNNING
  2. ContextBuilder.build():
     - System prompt (role, rules, constraints)
     - Task state (collectedData, pending questions)
     - Tool definitions (filtered by role + task)
     - Recent messages (sliding window)
     - Summary (if conversation is long)
  3. Guard checks: deadline, step count, token quota, cancellation
  4. Call LLM via Spring AI ChatClient (manual mode, no auto-execute)
  5. Parse LLM response:
     a. Text only → save assistant message → emit SSE deltas → check completion
     b. Tool calls → for each:
        - ToolExecutor validates name, args, permissions
        - If forbidden → return error to LLM
        - If needs user input → save pending action → WAITING_USER → break
        - Execute tool → save result → emit SSE
     c. Feed tool results back → goto step 3
  6. Check completion:
     - Goal met → SUCCEEDED
     - Not met but can continue → goto step 3
     - Cannot continue → FAILED / LIMIT_REACHED
```

### Spring AI Integration

**Strategy**: Manual control — không dùng auto-execute tool loop.

```java
// Pseudo-code
ChatClient chatClient = ChatClient.builder(chatModel).build();

while (canContinue(run)) {
    checkGuards(run); // deadline, steps, quota, cancellation
    
    List<Message> messages = contextBuilder.build(conversation, task, run);
    List<FunctionCallback> tools = toolRegistry.getToolsFor(user, task);
    
    ChatResponse response = chatClient.prompt()
        .messages(messages)
        .functions(tools)
        .call()
        .chatResponse();
    
    if (response.hasToolCalls()) {
        for (ToolCall call : response.getToolCalls()) {
            ToolResult result = toolExecutor.execute(call, agentContext);
            saveToolExecution(run, call, result);
            emitSSE(run, "tool.completed", result);
            // Add to context for next iteration
        }
    } else {
        String text = response.getResult().getOutput().getText();
        saveAssistantMessage(conversation, run, text);
        emitSSE(run, "assistant.delta", text);
    }
}

finalizeRun(run); // SUCCEEDED, FAILED, or LIMIT_REACHED
```

### Limits (configurable via application.yaml)

```yaml
agent:
  limits:
    max-model-steps: 8
    max-tool-calls: 12
    max-model-attempts: 16
    max-validation-repairs: 2
    active-timeout: 120s
  retry:
    model-max-retries: 2
    safe-tool-max-retries: 2
  context:
    max-input-tokens: 26000
    output-reserve: 4000
    safety-margin: 2000
    history-window-size: 10
```

---

## 5. Tool System

### Tool Definition

```java
public interface AgentTool {
    String getName();
    String getDescription();
    JsonSchema getInputSchema();
    ToolMetadata getMetadata(); // capabilityGroup, effectType, requiredRoles, timeout
    ToolResult execute(JsonNode arguments, AgentContext context);
}
```

### Tool Metadata

```java
public record ToolMetadata(
    String capabilityGroup,      // "common", "lecturer", "student"
    EffectType effectType,       // READ, TASK_MODIFY, DOMAIN_WRITE
    Set<String> requiredRoles,   // {"ROLE_STUDENT"}, {"ROLE_LECTURER"}
    Duration timeout,
    int outputLimitBytes
) {}
```

### Tool Registry

| Group | Tool | Effect | Roles |
|-------|------|--------|-------|
| Common | `listMyClasses` | READ | ALL |
| Common | `listVisibleAssignments` | READ | ALL |
| Common | `getAssignment` | READ | ALL |
| Student | `getMySubmission` | READ | STUDENT |
| Student | `getMyVisibleResult` | READ | STUDENT |
| Student | `recommendAssignments` | READ | STUDENT |
| Student | `listMyPendingAssignments` | READ | STUDENT |
| Lecturer | `getTestStepCapabilities` | READ | LECTURER |
| Lecturer | `listAllowedImages` | READ | LECTURER |
| Lecturer | `listClassStudents` | READ | LECTURER |
| Lecturer | `getSubmissionsForAssignment` | READ | LECTURER |
| Lecturer | `createAssignment` | DOMAIN_WRITE | LECTURER |

### Tool Execution Flow

```
LLM proposes tool call
  → ToolExecutor receives (toolName, arguments, agentContext)
  → Step 1: ToolRegistry.find(toolName) — exists?
  → Step 2: Schema validation — arguments match JSON schema?
  → Step 3: Permission check — user's role includes tool's requiredRoles?
  → Step 4: Execute tool — calls domain service via Feign client
  → Step 5: Domain service checks resource-level permission
  → Step 6: Sanitize result — no secrets, no stack traces, size limit
  → Return ToolResult(success, code, data, retryable, correlationId)
```

### Tool Result

```json
{
  "success": true,
  "code": "OK",
  "retryable": false,
  "message": "Found 3 assignments",
  "data": { ... },
  "correlationId": "trace-abc"
}
```

Error example:
```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "retryable": false,
  "message": "Cấu hình test step chưa hợp lệ",
  "fieldErrors": [{"field": "steps[2].expectedStatus", "message": "Phải là HTTP status hợp lệ"}],
  "data": null,
  "correlationId": "trace-123"
}
```

---

## 6. Context Management

### Context Builder — mỗi vòng LLM

```
1. Nạp task state từ DB (goal, collectedData, pending questions)
2. Chọn tool definitions: role ∩ task ∩ state
3. Giữ tool call/result pairs đang xử lý (đúng toolCallId)
4. Lấy recent messages (sliding window, mặc định 10 lượt)
5. Thêm conversation summary nếu có
6. Filter: bỏ secrets, redact sensitive fields
7. Tính token budget:
   - Nếu vượt: bỏ messages cũ nhất, rút gọn tool results, phân trang data
   - Nếu vẫn vượt: yêu cầu thu hẹp scope
8. Assemble final messages array
```

### System Prompt Template

```
Bạn là AI assistant của hệ thống WGS (Web Grading System). Hỗ trợ {role} trong việc {capabilities}.

Quy tắc:
- Hỏi lại khi thiếu thông tin, đưa ra 3-4 lựa chọn khi phù hợp
- Không tuyên bố đã thực hiện thao tác khi chưa có kết quả backend
- Trả lời bằng tiếng Việt, chuyên nghiệp, ngắn gọn
- Khi tra cứu dữ liệu, dùng tool được cấp — không bịa dữ liệu
- Khi tạo bài tập: thu thập đủ thông tin → đề xuất cấu hình → chờ xác nhận → gọi tool tạo

Trạng thái hiện tại: {taskState}
```

### Summarization

- Trigger: khi messages vượt `history-window-size` + budget
- Lưu: `ai_conversation_summary` với `through_message_id`
- Giữ: decisions, IDs, constraints, open questions
- Không tóm tắt mỗi vòng — chỉ khi cần

---

## 7. Conversational Flow — Tạo bài tập (Giảng viên)

### Sequence

```
User: "Tạo bài REST API cho lớp A"
Agent: (gọi tool listMyClasses để tìm lớp A)
Agent: "Tôi tìm thấy lớp 'CS101 - Lập trình Web'. Bạn muốn kiểm tra:
  1. HTTP Request test (kiểm tra API endpoints)
  2. Database Query test (kiểm tra dữ liệu sau API call)
  3. DB Schema check (kiểm tra cấu trúc database)
  4. DB Migration (kiểm tra lịch sử migration)
  Hoặc mô tả yêu cầu cụ thể."

User: "1 và 2, kiểm tra CRUD Products"
Agent: (gọi tool getTestStepCapabilities, listAllowedImages)
Agent: "Tôi đề xuất bài tập:
  **Tên**: CRUD REST API Products
  **Mô tả**: Xây dựng API CRUD quản lý sản phẩm với PostgreSQL
  **Deadline**: 01/11/2026 23:59 (ICT)
  **Docker Images**: openjdk:21, postgres:16
  **Test Plan** (5 steps):
    1. HTTP POST /products (tạo) → expect 201
    2. HTTP GET /products (danh sách) → expect 200 + JSON array
    3. DB_QUERY: SELECT * FROM products → expect 1 row
    4. HTTP PUT /products/{id} → expect 200
    5. HTTP DELETE /products/{id} → expect 200 hoặc 204
  
  Bạn muốn sửa gì không?"

User: "Thêm step kiểm tra schema, sửa deadline 15/11"
Agent: "Đã cập nhật:
  **Deadline**: 15/11/2026 23:59
  **Thêm Step 6**: DB_SCHEMA_CHECK — kiểm tra bảng `products` có cột `id`, `name`, `price`, `created_at`
  
  Tổng 6 steps. Bạn muốn tạo bài tập này không?"

User: "Tạo đi"
Agent: (gọi tool createAssignment với payload đã thu thập)
  → ToolExecutor validate → gọi Course Service API
Agent: "✅ Đã tạo bài tập 'CRUD REST API Products' (ID: abc-123)
  Trạng thái: DRAFT trong Course Service.
  Bạn có thể publish bài tập từ trang quản lý bài tập."
```

### Working State (trong ai_task.state_json)

```json
{
  "type": "CREATE_ASSIGNMENT",
  "classId": "class-uuid",
  "collectedData": {
    "title": "CRUD REST API Products",
    "description": "Xây dựng API CRUD...",
    "deadline": "2026-11-15T23:59:00+07:00",
    "allowedImages": ["openjdk:21", "postgres:16"],
    "testPlans": [{
      "name": "CRUD Products",
      "steps": [
        {"type": "HTTP_REQUEST", "method": "POST", "path": "/products", ...},
        {"type": "DB_QUERY", "query": "SELECT * FROM products", ...},
        {"type": "DB_SCHEMA_CHECK", "table": "products", "columns": [...]}
      ]
    }]
  },
  "confirmedByUser": false
}
```

---

## 8. Frontend — Chat Sidebar

### Layout

Sidebar bên phải, ~380px, toggleable từ header icon hoặc FAB:

```
+------------------------------------------+-------------------+
|                                          | 💬 AI Assistant   |
|          Main App Content                |                   |
|          (giữ nguyên)                    | [Conv selector ▼] |
|                                          |                   |
|                                          | 📩 User message   |
|                                          | 🤖 Agent response |
|                                          |   📦 Tool: list.. |
|                                          | 📩 User: "OK"     |
|                                          | 🤖 ✅ Created!     |
|                                          |                   |
|                                          | [Type message...] |
+------------------------------------------+-------------------+
```

### Components

| Component | Purpose |
|-----------|---------|
| `AiChatSidebar` | Root container, toggle visibility |
| `ConversationSelector` | Dropdown — chọn/tạo conversation |
| `MessageList` | Scrollable list, auto-scroll to bottom |
| `UserMessage` | Bubble bên phải |
| `AssistantMessage` | Bubble bên trái, markdown rendering |
| `ToolExecutionCard` | Collapsible card — tool name + status |
| `StreamingIndicator` | Typing animation khi LLM đang stream |
| `ChatInput` | Textarea + send, Enter/Shift+Enter |
| `RunStatusBar` | Top bar hiển thị run status |

### API Integration

```typescript
// shared/api/endpoints/ai.ts
export const aiApi = {
  createConversation: () => POST('/api/v1/ai/conversations'),
  listConversations: () => GET('/api/v1/ai/conversations'),
  sendMessage: (convId, body) => POST(`/api/v1/ai/conversations/${convId}/messages`, body),
  getMessages: (convId, params) => GET(`/api/v1/ai/conversations/${convId}/messages`, params),
  getRunStatus: (runId) => GET(`/api/v1/ai/runs/${runId}`),
  cancelRun: (runId) => POST(`/api/v1/ai/runs/${runId}/cancel`),
  subscribeRunEvents: (runId) => new EventSource(`/api/v1/ai/runs/${runId}/events`),
}
```

### React Query Hooks

```typescript
// features/ai-chat/hooks/
useConversations()        // list + create
useMessages(convId)       // paginated messages
useSendMessage()          // mutation → POST + subscribe SSE
useRunEvents(runId)       // SSE subscription, update messages cache
```

---

## 9. Package Structure (Final)

### Backend

```
ai-assistant-service/src/main/java/.../ai_assistant_service/
├── AiAssistantServiceApplication.java       # (giữ nguyên)
├── client/                                  # (giữ nguyên + thêm SubmissionClient)
│   ├── CourseServiceClient.java             # (giữ nguyên)
│   ├── ResultServiceClient.java             # (giữ nguyên)
│   ├── SubmissionServiceClient.java         # (MỚI)
│   └── dto/                                 # (giữ nguyên + thêm DTOs)
├── config/
│   ├── AiModelProperties.java              # (giữ nguyên)
│   ├── GatewayTrustProperties.java          # (giữ nguyên)
│   ├── SecurityConfig.java                  # (giữ nguyên)
│   ├── SpringAiConfig.java                  # (MỚI) Spring AI ChatModel bean
│   └── AgentProperties.java                 # (MỚI) Limits & retry config
├── controller/
│   ├── AiExplanationController.java         # (giữ nguyên)
│   ├── ChatController.java                  # (MỚI)
│   ├── RunController.java                   # (MỚI)
│   └── OperationController.java             # (MỚI)
├── domain/
│   ├── conversation/
│   │   ├── Conversation.java                # Entity
│   │   ├── Message.java                     # Entity
│   │   └── ConversationSummary.java         # Entity
│   ├── run/
│   │   ├── Run.java                         # Entity
│   │   ├── RunStatus.java                   # Enum
│   │   ├── RunEvent.java                    # Entity
│   │   └── ToolExecution.java               # Entity
│   ├── task/
│   │   ├── Task.java                        # Entity
│   │   └── PendingAction.java               # Entity
│   └── operation/
│       ├── Operation.java                   # Entity
│       └── OperationStatus.java             # Enum
├── orchestrator/
│   ├── AgentOrchestrator.java               # Vòng lặp agent chính
│   ├── ContextBuilder.java                  # Dựng context/prompt cho LLM
│   ├── RunWorker.java                       # Async run processing
│   └── GuardChecker.java                    # Limits, deadline, cancellation
├── tools/
│   ├── AgentTool.java                       # Interface
│   ├── ToolDefinition.java                  # Metadata record
│   ├── ToolResult.java                      # Result record
│   ├── ToolRegistry.java                    # Registry + selection
│   ├── ToolExecutor.java                    # Validation + execution
│   ├── common/
│   │   ├── ListMyClassesTool.java
│   │   ├── ListVisibleAssignmentsTool.java
│   │   └── GetAssignmentTool.java
│   ├── student/
│   │   ├── GetMySubmissionTool.java
│   │   ├── GetMyVisibleResultTool.java
│   │   ├── RecommendAssignmentsTool.java
│   │   └── ListMyPendingAssignmentsTool.java
│   └── lecturer/
│       ├── GetTestStepCapabilitiesTool.java
│       ├── ListAllowedImagesTool.java
│       ├── ListClassStudentsTool.java
│       ├── GetSubmissionsForAssignmentTool.java
│       └── CreateAssignmentTool.java
├── dto/                                     # (giữ nguyên + thêm agent DTOs)
├── entity/                                  # (giữ nguyên)
├── repository/                              # (giữ nguyên + thêm repos mới)
│   ├── AiExplanationRepository.java         # (giữ nguyên)
│   ├── ConversationRepository.java          # (MỚI)
│   ├── MessageRepository.java              # (MỚI)
│   ├── RunRepository.java                  # (MỚI)
│   ├── TaskRepository.java                 # (MỚI)
│   ├── OperationRepository.java            # (MỚI)
│   ├── ToolExecutionRepository.java        # (MỚI)
│   ├── PendingActionRepository.java        # (MỚI)
│   └── RunEventRepository.java             # (MỚI)
├── security/                                # (giữ nguyên)
└── service/                                 # (giữ nguyên — explanation only)
    ├── AiExplanationService.java            # (giữ nguyên)
    ├── DataSanitizer.java                   # (giữ nguyên)
    ├── LlmClient.java                       # (giữ nguyên — explanation only)
    └── PromptBuilder.java                   # (giữ nguyên — explanation only)
```

### Frontend

```
web-grading-system-fe/src/
├── features/
│   └── ai-chat/                             # (MỚI)
│       ├── AiChatSidebar.tsx                # Root sidebar component
│       ├── components/
│       │   ├── ConversationSelector.tsx
│       │   ├── MessageList.tsx
│       │   ├── UserMessage.tsx
│       │   ├── AssistantMessage.tsx
│       │   ├── ToolExecutionCard.tsx
│       │   ├── StreamingIndicator.tsx
│       │   ├── ChatInput.tsx
│       │   └── RunStatusBar.tsx
│       ├── hooks/
│       │   ├── useConversations.ts
│       │   ├── useMessages.ts
│       │   ├── useSendMessage.ts
│       │   └── useRunEvents.ts
│       └── types.ts
├── shared/
│   ├── api/endpoints/
│   │   └── ai.ts                            # (MỚI) Agent API endpoints
│   └── types/
│       └── aiAgent.ts                       # (MỚI) Agent types
```

---

## 10. Error Handling & Retry (mục 11, 24)

| Tình huống | Xử lý | Kết quả |
|------------|-------|---------|
| Thiếu thông tin | Hỏi câu cụ thể, options | Chờ user |
| Model 429/5xx | Retry ≤2, backoff + jitter | Báo gián đoạn nếu hết lượt |
| Arguments sai schema | Validate, trả lỗi cho model sửa ≤2 | Hỏi user nếu không sửa được |
| Forbidden | Dừng thao tác, không retry | Từ chối rõ ràng |
| Domain validation fail | Trả fieldErrors, agent giải thích | Sửa qua chat |
| GET timeout | Retry ≤2 | Báo thiếu dữ liệu |
| POST timeout | Tra operationId trước retry | Không kết luận thất bại |
| Hết budget | Lưu task state, dừng | Báo phần đã xong |
| User hủy | Ngừng bước tiếp, check operation | Nêu rõ đã thực hiện |

---

## 11. Security Considerations

- LLM không được truyền userId/role để tự cấp quyền
- ClassId, submissionId do model đề xuất đều là untrusted — phải kiểm tra
- Không gửi JWT, API key, gateway secret cho model
- Hidden tests, đáp án không gửi cho luồng sinh viên
- Tool result không trả stack trace, SQL, token
- Bộ lọc nằm trước khi gửi model, không chỉ prompt instruction
- Service-to-service auth dùng cơ chế hiện có (gateway secret + headers)

---

## 12. Dependencies cần thêm vào pom.xml

```xml
<!-- Spring AI OpenAI starter -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
</dependency>

<!-- Spring AI BOM (trong dependencyManagement) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>${spring-ai.version}</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
```

> **Note**: Cần xác định version Spring AI tương thích với Spring Boot 4.1.0 trước khi code. Kiểm tra [Spring AI compatibility matrix](https://docs.spring.io/spring-ai/reference/getting-started.html).

---

## 13. Acceptance Scenarios (từ mục 16 tài liệu)

1. ✅ GV tạo bài qua chat, sửa 2 lần, xác nhận → tạo đúng 1 Assignment
2. ✅ SV đoán submissionId người khác → bị từ chối trước khi data vào LLM
3. ✅ Log có hidden test → bị lọc trước context
4. ✅ Model yêu cầu tool trái quyền → backend từ chối
5. ✅ Course Service timeout → đối soát operationId, không tạo trùng
6. ✅ Model hỏng sau tạo bài → UI nhận assignmentId thật từ backend
7. ✅ Tool loop vô hạn → dừng đúng giới hạn
8. ✅ SSE reconnect → không tạo 2 run cho cùng clientMessageId
9. ✅ Hội thoại vượt budget → giữ task state, báo phần đã xong
10. ✅ User hủy khi operation đã commit → không báo đã hoàn tác

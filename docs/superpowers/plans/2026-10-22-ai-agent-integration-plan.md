# AI Agent Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement full-featured conversational AI Agent in `ai-assistant-service` and frontend chat drawer for PTIT Web Grading System, supporting student assistance (class/assignment search, submission/result lookup, grading explanation) and lecturer conversational assignment creation with safety guardrails.

**Architecture:** Extend `ai-assistant-service` with PostgreSQL persistence for conversations, runs, tasks, and tool executions. The orchestrator drives an application-controlled tool-calling loop over Spring AI / OpenAI-compatible ChatModel, executing domain tools via Feign clients with strict role and resource authorization. Real-time token deltas and tool lifecycle events stream to the React frontend via Server-Sent Events (SSE).

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring Data JPA, PostgreSQL (Flyway), Spring Cloud OpenFeign, Spring AI / OpenAI-compatible ChatModel, React 19, TypeScript, Ant Design v6, TanStack React Query v5, Server-Sent Events (SSE).

## Global Constraints
- Preserve existing AI explanation service (`AiExplanationService`, `AiExplanationController`, `LlmClient`, `PromptBuilder`, `ai_explanations` table) intact without breaking changes.
- All outbound Feign calls to sibling services (`course-service`, `submission-service`, `result-service`) must forward `X-User-Id`, `X-User-Roles`, and `X-Gateway-Secret`.
- Student tools must enforce student ownership (e.g. students can only view their own submissions/results; hidden test cases/solutions must never leak).
- Lecturer tools must enforce lecturer ownership and permission checks before executing domain modifications.
- Conversational assignment creation must validate test step types (`HTTP_REQUEST`, `DB_QUERY`, `DB_SCHEMA_CHECK`, `DB_MIGRATION`) and whitelist Docker images.
- Idempotency key must be checked on state-changing operations via `ai_operation` to prevent duplicate resource creations.
- No secrets, raw exceptions, or internal SQL traces leaked in tool results or LLM context.

---

## File Structure Map

### Backend: `ai-assistant-service`
```
ai-assistant-service/
├── src/main/resources/
│   ├── db/migration/
│   │   └── V3__2026-10-22__init_ai_agent_tables.sql       # New tables for agent
│   └── application.yaml                                  # Agent limits & retry config
├── src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/
│   ├── client/
│   │   ├── SubmissionServiceClient.java                  # Feign client for submissions
│   │   ├── CourseServiceClient.java                      # Extended with class/assignment queries
│   │   └── dto/                                          # Inter-service DTO mirrors
│   ├── config/
│   │   ├── AgentProperties.java                          # Config properties for agent limits
│   │   └── SpringAiConfig.java                           # OpenAI ChatModel configuration bean
│   ├── controller/
│   │   ├── ChatController.java                           # /api/v1/ai/conversations/**
│   │   ├── RunController.java                            # /api/v1/ai/runs/** (SSE & polling)
│   │   └── OperationController.java                      # /api/v1/ai/operations/**
│   ├── domain/
│   │   ├── conversation/
│   │   │   ├── Conversation.java                         # Conversation entity
│   │   │   ├── Message.java                              # Message entity
│   │   │   └── ConversationSummary.java                  # Summary entity
│   │   ├── run/
│   │   │   ├── Run.java                                  # Run entity
│   │   │   ├── RunStatus.java                            # Status enum
│   │   │   ├── RunEvent.java                             # SSE Event entity
│   │   │   └── ToolExecution.java                        # Tool audit entity
│   │   ├── task/
│   │   │   ├── Task.java                                 # Working task entity
│   │   │   └── PendingAction.java                        # Pending action entity
│   │   └── operation/
│   │       ├── Operation.java                            # Idempotent command entity
│   │       └── OperationStatus.java                      # Status enum
│   ├── dto/
│   │   ├── request/                                      # Chat & run request DTOs
│   │   └── response/                                     # Chat, run, event DTOs
│   ├── orchestrator/
│   │   ├── AgentOrchestrator.java                        # Main step execution loop
│   │   ├── ContextBuilder.java                           # Prompt & message assembler
│   │   ├── GuardChecker.java                             # Token budget, steps & timeout check
│   │   ├── RunWorker.java                                # Async run processor
│   │   └── SseBroadcaster.java                           # In-memory SSE emitter manager
│   ├── repository/                                       # Spring Data repositories
│   └── tools/
│       ├── AgentTool.java                                # Tool interface
│       ├── ToolDefinition.java                           # Tool metadata
│       ├── ToolResult.java                               # Execution result
│       ├── ToolRegistry.java                             # Dynamic registry
│       ├── ToolExecutor.java                             # Validation & dispatch
│       ├── common/                                       # ListMyClasses, ListVisibleAssignments, GetAssignment
│       ├── student/                                      # GetMySubmission, GetMyVisibleResult, Recommend, Pending
│       └── lecturer/                                     # Capabilities, AllowedImages, Students, Submissions, CreateAssignment
```

### Frontend: `web-grading-system-fe`
```
web-grading-system-fe/src/
├── shared/
│   ├── types/
│   │   └── aiAgent.ts                                    # TypeScript types for agent & runs
│   ├── api/endpoints/
│   │   └── ai.ts                                         # Endpoints for conversation/run/SSE
│   └── layout/
│       └── AppLayout.tsx                                 # Chat toggle button & sidebar mounting
└── features/
    └── ai-chat/
        ├── AiChatSidebar.tsx                             # Main drawer/sidebar container
        ├── components/
        │   ├── ConversationSelector.tsx                  # Dropdown to switch/create convs
        │   ├── MessageList.tsx                           # Virtualized / scrollable list
        │   ├── UserMessage.tsx                           # Bubble user
        │   ├── AssistantMessage.tsx                      # Bubble markdown assistant
        │   ├── ToolExecutionCard.tsx                     # Collapsible card for tool logs
        │   ├── StreamingIndicator.tsx                    # Typing animation
        │   ├── ChatInput.tsx                             # Prompt input + send button
        │   └── RunStatusBar.tsx                          # Status bar for running/waiting
        └── hooks/
            ├── useConversations.ts                       # React Query conv hooks
            ├── useMessages.ts                            # Messages query
            ├── useSendMessage.ts                         # Message mutation & run trigger
            └── useRunEvents.ts                           # EventSource SSE hook
```

---

## Phase 1: Foundation (Database & Configuration)

### Task 1: Database Migration for AI Agent Core Tables

**Files:**
- Create: `ai-assistant-service/src/main/resources/db/migration/V3__2026-10-22__init_ai_agent_tables.sql`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/migration/V3MigrationTest.java`

**Interfaces:**
- Consumes: PostgreSQL schema `ai_db`, existing `ai_explanations` table from V1 & V2.
- Produces: 9 new relational tables (`ai_conversation`, `ai_message`, `ai_run`, `ai_task`, `ai_conversation_summary`, `ai_operation`, `ai_tool_execution`, `ai_pending_action`, `ai_run_event`).

- [ ] **Step 1: Write the failing test for schema migration**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/migration/V3MigrationTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.migration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
public class V3MigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void testV3TablesExist() {
        String[] tables = {
            "ai_conversation", "ai_message", "ai_run", "ai_task",
            "ai_conversation_summary", "ai_operation", "ai_tool_execution",
            "ai_pending_action", "ai_run_event"
        };
        for (String table : tables) {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_name = ?",
                Integer.class, table
            );
            assertTrue(count != null && count > 0, "Table " + table + " should exist");
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=V3MigrationTest` in `ai-assistant-service`
Expected: FAIL with missing tables or migration not executed.

- [ ] **Step 3: Write minimal Flyway SQL migration**

Create `ai-assistant-service/src/main/resources/db/migration/V3__2026-10-22__init_ai_agent_tables.sql`:
```sql
-- V3__2026-10-22__init_ai_agent_tables.sql
-- Initializes AI Agent core entities: conversations, messages, runs, tasks, operations, audit

CREATE TABLE IF NOT EXISTS ai_conversation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id UUID NOT NULL,
    title VARCHAR(255),
    active_task_id UUID,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_conversation_owner ON ai_conversation(owner_id);

CREATE TABLE IF NOT EXISTS ai_message (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
    run_id UUID,
    role VARCHAR(20) NOT NULL, -- 'user', 'assistant', 'tool', 'system'
    content TEXT,
    tool_call_id VARCHAR(100),
    tool_name VARCHAR(100),
    tool_arguments JSONB,
    tool_result JSONB,
    client_message_id VARCHAR(100),
    sequence_no INTEGER NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_message_conversation ON ai_message(conversation_id, sequence_no);
CREATE UNIQUE INDEX IF NOT EXISTS uq_message_client_id ON ai_message(conversation_id, client_message_id)
    WHERE client_message_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS ai_run (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
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
CREATE INDEX IF NOT EXISTS idx_run_conversation ON ai_run(conversation_id);
CREATE INDEX IF NOT EXISTS idx_run_status ON ai_run(status) WHERE status IN ('QUEUED', 'RUNNING');

CREATE TABLE IF NOT EXISTS ai_task (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
    owner_id UUID NOT NULL,
    type VARCHAR(50), -- 'CREATE_ASSIGNMENT', 'QUERY', 'EXPLAIN'
    goal_json JSONB,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    state_json JSONB,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_task_conversation ON ai_task(conversation_id);

CREATE TABLE IF NOT EXISTS ai_conversation_summary (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES ai_conversation(id) ON DELETE CASCADE,
    through_message_id UUID NOT NULL,
    summary TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_summary_conversation ON ai_conversation_summary(conversation_id);

CREATE TABLE IF NOT EXISTS ai_operation (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID REFERENCES ai_task(id) ON DELETE SET NULL,
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
CREATE INDEX IF NOT EXISTS idx_operation_task ON ai_operation(task_id);

CREATE TABLE IF NOT EXISTS ai_tool_execution (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES ai_run(id) ON DELETE CASCADE,
    tool_call_id VARCHAR(100),
    tool_name VARCHAR(100) NOT NULL,
    arguments_redacted JSONB,
    result_redacted JSONB,
    status VARCHAR(20) NOT NULL,
    latency_ms INTEGER,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_tool_exec_run ON ai_tool_execution(run_id);

CREATE TABLE IF NOT EXISTS ai_pending_action (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id UUID REFERENCES ai_task(id) ON DELETE CASCADE,
    run_id UUID REFERENCES ai_run(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL, -- 'QUESTION', 'CONFIRM'
    question_json JSONB,
    options_json JSONB,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_pending_task ON ai_pending_action(task_id) WHERE status = 'PENDING';

CREATE TABLE IF NOT EXISTS ai_run_event (
    id BIGSERIAL PRIMARY KEY,
    run_id UUID NOT NULL REFERENCES ai_run(id) ON DELETE CASCADE,
    sequence INTEGER NOT NULL,
    type VARCHAR(50) NOT NULL,
    payload_json JSONB,
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_run_event_run ON ai_run_event(run_id, sequence);
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=V3MigrationTest`
Expected: PASS (9 tables verified in schema)

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/resources/db/migration/V3__2026-10-22__init_ai_agent_tables.sql ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/migration/V3MigrationTest.java
git commit -m "feat(ai-agent): add V3 database migration for agent tables"
```

---

### Task 2: JPA Domain Entities and Enums

**Files:**
- Create:
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/run/RunStatus.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/operation/OperationStatus.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/conversation/Conversation.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/conversation/Message.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/conversation/ConversationSummary.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/run/Run.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/run/RunEvent.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/run/ToolExecution.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/task/Task.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/task/PendingAction.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/operation/Operation.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/EntityMappingTest.java`

**Interfaces:**
- Consumes: Tables created in Task 1.
- Produces: JPA managed entity classes with JSONB fields mapped via `org.hibernate.annotations.JdbcTypeCode` or String representation.

- [ ] **Step 1: Write the failing test for entity mappings**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/EntityMappingTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.domain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@DataJpaTest
public class EntityMappingTest {

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void testPersistConversationAndMessage() {
        Conversation conversation = Conversation.builder()
                .ownerId(UUID.randomUUID())
                .title("Test Chat")
                .build();
        conversation = entityManager.persistAndFlush(conversation);
        assertNotNull(conversation.getId());

        Message message = Message.builder()
                .conversationId(conversation.getId())
                .role("user")
                .content("Hello Agent")
                .sequenceNo(1)
                .build();
        message = entityManager.persistAndFlush(message);
        assertNotNull(message.getId());

        Run run = Run.builder()
                .conversationId(conversation.getId())
                .status(RunStatus.QUEUED)
                .version(1)
                .build();
        run = entityManager.persistAndFlush(run);
        assertNotNull(run.getId());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=EntityMappingTest`
Expected: FAIL with compilation error (entity classes do not exist).

- [ ] **Step 3: Implement domain enums and entities**

Create `RunStatus.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run;

public enum RunStatus {
    QUEUED,
    RUNNING,
    WAITING_USER,
    WAITING_APPROVAL,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    LIMIT_REACHED
}
```

Create `OperationStatus.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.domain.operation;

public enum OperationStatus {
    PENDING,
    IN_PROGRESS,
    SUCCEEDED,
    FAILED,
    RECONCILING
}
```

Create `Conversation.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_conversation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "title")
    private String title;

    @Column(name = "active_task_id")
    private UUID activeTaskId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

Create `Message.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "tool_call_id", length = 100)
    private String toolCallId;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_arguments", columnDefinition = "jsonb")
    private String toolArguments;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_result", columnDefinition = "jsonb")
    private String toolResult;

    @Column(name = "client_message_id", length = 100)
    private String clientMessageId;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
```

Create `Run.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Run {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RunStatus status;

    @Column(name = "parent_run_id")
    private UUID parentRunId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checkpoint_json", columnDefinition = "jsonb")
    private String checkpointJson;

    @Builder.Default
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limits_json", columnDefinition = "jsonb")
    private String limitsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "usage_json", columnDefinition = "jsonb")
    private String usageJson;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}
```

Create `Task.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "type", length = 50)
    private String type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "goal_json", columnDefinition = "jsonb")
    private String goalJson;

    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private String status = "ACTIVE";

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "state_json", columnDefinition = "jsonb")
    private String stateJson;

    @Builder.Default
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

Create `Operation.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.domain.operation;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_operation")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Operation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(name = "command_type", nullable = false, length = 50)
    private String commandType;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_json", nullable = false, columnDefinition = "jsonb")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private OperationStatus status = OperationStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_ref", columnDefinition = "jsonb")
    private String resultRef;

    @Builder.Default
    @Column(name = "attempts", nullable = false)
    private Integer attempts = 0;

    @Column(name = "next_check_at")
    private Instant nextCheckAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
```

Create `RunEvent.java`, `ToolExecution.java`, `PendingAction.java`, `ConversationSummary.java` following the same JPA pattern.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=EntityMappingTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/ ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/domain/
git commit -m "feat(ai-agent): add domain entities and enums"
```

---

### Task 3: Spring Data JPA Repositories

**Files:**
- Create:
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/ConversationRepository.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/MessageRepository.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/RunRepository.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/TaskRepository.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/OperationRepository.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/RunEventRepository.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/ToolExecutionRepository.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/ConversationRepositoryTest.java`

**Interfaces:**
- Consumes: Task 2 entities.
- Produces: Standard Spring Data `JpaRepository<T, UUID>` with pagination and custom query methods.

- [ ] **Step 1: Write the failing test for repositories**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/ConversationRepositoryTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
public class ConversationRepositoryTest {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private MessageRepository messageRepository;

    @Test
    void testConversationAndMessageQueries() {
        UUID ownerId = UUID.randomUUID();
        Conversation conv = conversationRepository.save(Conversation.builder()
                .ownerId(ownerId)
                .title("My Test Chat")
                .build());

        Page<Conversation> page = conversationRepository.findByOwnerIdOrderByUpdatedAtDesc(ownerId, PageRequest.of(0, 10));
        assertEquals(1, page.getTotalElements());

        Message msg1 = messageRepository.save(Message.builder()
                .conversationId(conv.getId())
                .role("user")
                .content("Prompt 1")
                .sequenceNo(1)
                .clientMessageId("client-123")
                .build());

        List<Message> messages = messageRepository.findByConversationIdOrderBySequenceNoAsc(conv.getId());
        assertEquals(1, messages.size());

        Optional<Message> byClient = messageRepository.findByConversationIdAndClientMessageId(conv.getId(), "client-123");
        assertTrue(byClient.isPresent());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ConversationRepositoryTest`
Expected: FAIL with compilation error (repositories do not exist).

- [ ] **Step 3: Implement repository interfaces**

Create `ConversationRepository.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Page<Conversation> findByOwnerIdOrderByUpdatedAtDesc(UUID ownerId, Pageable pageable);
    Optional<Conversation> findByIdAndOwnerId(UUID id, UUID ownerId);
}
```

Create `MessageRepository.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MessageRepository extends JpaRepository<Message, UUID> {
    List<Message> findByConversationIdOrderBySequenceNoAsc(UUID conversationId);
    Page<Message> findByConversationIdOrderBySequenceNoAsc(UUID conversationId, Pageable pageable);
    Optional<Message> findByConversationIdAndClientMessageId(UUID conversationId, String clientMessageId);

    @Query("SELECT COALESCE(MAX(m.sequenceNo), 0) FROM Message m WHERE m.conversationId = :conversationId")
    int findMaxSequenceNo(UUID conversationId);
}
```

Create `RunRepository.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;

import java.util.List;
import java.util.UUID;

@Repository
public interface RunRepository extends JpaRepository<Run, UUID> {
    List<Run> findByConversationIdOrderByCreatedAtDesc(UUID conversationId);
    List<Run> findByStatusIn(List<RunStatus> statuses);
}
```

Create `TaskRepository.java`, `OperationRepository.java`, `RunEventRepository.java`, `ToolExecutionRepository.java`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ConversationRepositoryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/ ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/
git commit -m "feat(ai-agent): add Spring Data repositories for agent persistence"
```

---

### Task 4: Configuration & Agent Properties

**Files:**
- Modify: `ai-assistant-service/pom.xml`
- Modify: `ai-assistant-service/src/main/resources/application.yaml`
- Create:
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/config/AgentProperties.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/config/SpringAiConfig.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/config/AgentPropertiesTest.java`

**Interfaces:**
- Consumes: `application.yaml` configuration keys under `agent.limits`, `agent.retry`, `agent.context`.
- Produces: Type-safe `@ConfigurationProperties(prefix = "agent") AgentProperties` and `ChatModel` bean (or OpenAI client factory for agent step execution).

- [ ] **Step 1: Write the failing test for AgentProperties binding**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/config/AgentPropertiesTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class AgentPropertiesTest {

    @Autowired
    private AgentProperties agentProperties;

    @Test
    void testPropertiesBoundSuccessfully() {
        assertNotNull(agentProperties);
        assertEquals(8, agentProperties.getLimits().getMaxModelSteps());
        assertEquals(12, agentProperties.getLimits().getMaxToolCalls());
        assertEquals(120, agentProperties.getLimits().getActiveTimeoutSeconds());
        assertEquals(10, agentProperties.getContext().getHistoryWindowSize());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=AgentPropertiesTest`
Expected: FAIL (AgentProperties bean does not exist).

- [ ] **Step 3: Add properties class and YAML configuration**

Create `AgentProperties.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "agent")
public class AgentProperties {

    private Limits limits = new Limits();
    private Retry retry = new Retry();
    private Context context = new Context();

    @Data
    public static class Limits {
        private int maxModelSteps = 8;
        private int maxToolCalls = 12;
        private int maxModelAttempts = 16;
        private int maxValidationRepairs = 2;
        private int activeTimeoutSeconds = 120;
    }

    @Data
    public static class Retry {
        private int modelMaxRetries = 2;
        private int safeToolMaxRetries = 2;
    }

    @Data
    public static class Context {
        private int maxInputTokens = 26000;
        private int outputReserve = 4000;
        private int safetyMargin = 2000;
        private int historyWindowSize = 10;
    }
}
```

Update `application.yaml`:
```yaml
agent:
  limits:
    max-model-steps: 8
    max-tool-calls: 12
    max-model-attempts: 16
    max-validation-repairs: 2
    active-timeout-seconds: 120
  retry:
    model-max-retries: 2
    safe-tool-max-retries: 2
  context:
    max-input-tokens: 26000
    output-reserve: 4000
    safety-margin: 2000
    history-window-size: 10
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=AgentPropertiesTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/resources/application.yaml ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/config/AgentProperties.java ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/config/AgentPropertiesTest.java
git commit -m "feat(ai-agent): add agent configuration properties"
```

---

## Phase 2: Core Chat API & SSE Streaming

### Task 5: Request & Response DTOs

**Files:**
- Create:
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/request/CreateConversationRequest.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/request/SendMessageRequest.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/response/ConversationResponse.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/response/MessageResponse.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/response/SendMessageResponse.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/response/RunResponse.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/response/RunEventDto.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/response/OperationResponse.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/DtoSerializationTest.java`

**Interfaces:**
- Consumes: Jackson JSON mapper.
- Produces: Immutable DTO records/classes for REST contracts.

- [ ] **Step 1: Write the failing test for DTO serialization**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/DtoSerializationTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request.SendMessageRequest;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.SendMessageResponse;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DtoSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void testSendMessageRequestAndResponse() throws Exception {
        String json = """
                {"text":"Tạo bài tập REST API","clientMessageId":"msg-1"}
                """;
        SendMessageRequest req = objectMapper.readValue(json, SendMessageRequest.class);
        assertEquals("Tạo bài tập REST API", req.getText());
        assertEquals("msg-1", req.getClientMessageId());

        UUID convId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        SendMessageResponse resp = new SendMessageResponse(convId, runId, msgId);
        String out = objectMapper.writeValueAsString(resp);
        assertNotNull(out);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=DtoSerializationTest`
Expected: FAIL (DTO classes do not exist).

- [ ] **Step 3: Implement request and response DTOs**

Create `CreateConversationRequest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateConversationRequest {
    private String title;
}
```

Create `SendMessageRequest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRequest {
    @NotBlank(message = "Message text cannot be empty")
    private String text;
    private String clientMessageId;
    private Integer expectedTaskVersion;
}
```

Create `SendMessageResponse.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response;

import java.util.UUID;

public record SendMessageResponse(
        UUID conversationId,
        UUID runId,
        UUID messageId
) {}
```

Create `ConversationResponse.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID ownerId,
        String title,
        UUID activeTaskId,
        Instant createdAt,
        Instant updatedAt
) {}
```

Create `MessageResponse.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID runId,
        String role,
        String content,
        String toolCallId,
        String toolName,
        String toolArguments,
        String toolResult,
        String clientMessageId,
        Integer sequenceNo,
        Instant createdAt
) {}
```

Create `RunResponse.java`, `RunEventDto.java`, `OperationResponse.java`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=DtoSerializationTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/ ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/
git commit -m "feat(ai-agent): add request and response DTOs"
```

---

### Task 6: Chat & Run REST Controllers

**Files:**
- Create:
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/ConversationService.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/controller/ChatController.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/controller/RunController.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/controller/OperationController.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/controller/ChatControllerTest.java`

**Interfaces:**
- Consumes: `SecurityConfig`, `UserPrincipal`, `ConversationRepository`, `MessageRepository`, `RunRepository`.
- Produces:
  - `POST /api/v1/ai/conversations` (201 Created)
  - `GET /api/v1/ai/conversations` (200 OK, paginated)
  - `GET /api/v1/ai/conversations/{id}` (200 OK)
  - `DELETE /api/v1/ai/conversations/{id}` (204 No Content)
  - `POST /api/v1/ai/conversations/{id}/messages` (202 Accepted)
  - `GET /api/v1/ai/conversations/{id}/messages` (200 OK, paginated)
  - `GET /api/v1/ai/runs/{id}` (200 OK)
  - `POST /api/v1/ai/runs/{id}/cancel` (200 OK)

- [ ] **Step 1: Write the failing controller test**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/controller/ChatControllerTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.GatewayTrustProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.SecurityConfig;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.ConversationResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.SendMessageResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.security.HeaderAuthenticationFilter;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.ConversationService;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ChatController.class)
@Import({SecurityConfig.class, HeaderAuthenticationFilter.class})
@EnableConfigurationProperties(GatewayTrustProperties.class)
@TestPropertySource(properties = "gateway.security.secret=" + ChatControllerTest.SECRET)
public class ChatControllerTest {

    static final String SECRET = "test-secret";
    private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private FilterChainProxy securityFilterChain;

    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilters(securityFilterChain)
                .build();
    }

    @Test
    void createConversation_returns201() throws Exception {
        UUID convId = UUID.randomUUID();
        when(conversationService.createConversation(any(), any()))
                .thenReturn(new ConversationResponse(convId, UUID.fromString(USER_ID), "New Chat", null, Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/v1/ai/conversations")
                        .header("X-Gateway-Secret", SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Roles", "ROLE_STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"New Chat\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(convId.toString()));
    }

    @Test
    void sendMessage_returns202Accepted() throws Exception {
        UUID convId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID msgId = UUID.randomUUID();
        when(conversationService.sendMessage(eq(convId), any(), any()))
                .thenReturn(new SendMessageResponse(convId, runId, msgId));

        mockMvc.perform(post("/api/v1/ai/conversations/{id}/messages", convId)
                        .header("X-Gateway-Secret", SECRET)
                        .header("X-User-Id", USER_ID)
                        .header("X-User-Roles", "ROLE_STUDENT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Tìm bài tập cho tôi\",\"clientMessageId\":\"c1\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value(runId.toString()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ChatControllerTest`
Expected: FAIL (Controller and service do not exist).

- [ ] **Step 3: Implement ConversationService and ChatController**

Create `ConversationService.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request.CreateConversationRequest;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request.SendMessageRequest;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.ConversationResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.MessageResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.SendMessageResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.RunRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.security.UserPrincipal;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final RunRepository runRepository;

    @Transactional
    public ConversationResponse createConversation(UserPrincipal principal, CreateConversationRequest request) {
        String title = (request != null && request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle().trim()
                : "Hội thoại mới";

        Conversation conv = Conversation.builder()
                .ownerId(principal.userId())
                .title(title)
                .build();
        conv = conversationRepository.save(conv);
        return mapToResponse(conv);
    }

    @Transactional(readOnly = true)
    public Page<ConversationResponse> listConversations(UserPrincipal principal, Pageable pageable) {
        return conversationRepository.findByOwnerIdOrderByUpdatedAtDesc(principal.userId(), pageable)
                .map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UserPrincipal principal, UUID conversationId) {
        Conversation conv = conversationRepository.findByIdAndOwnerId(conversationId, principal.userId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        return mapToResponse(conv);
    }

    @Transactional
    public void deleteConversation(UserPrincipal principal, UUID conversationId) {
        Conversation conv = conversationRepository.findByIdAndOwnerId(conversationId, principal.userId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        conversationRepository.delete(conv);
    }

    @Transactional
    public SendMessageResponse sendMessage(UUID conversationId, UserPrincipal principal, SendMessageRequest request) {
        Conversation conv = conversationRepository.findByIdAndOwnerId(conversationId, principal.userId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        if (request.getClientMessageId() != null && !request.getClientMessageId().isBlank()) {
            Optional<Message> existing = messageRepository.findByConversationIdAndClientMessageId(conversationId, request.getClientMessageId());
            if (existing.isPresent()) {
                Message msg = existing.get();
                return new SendMessageResponse(conversationId, msg.getRunId(), msg.getId());
            }
        }

        int nextSeq = messageRepository.findMaxSequenceNo(conversationId) + 1;

        Run run = Run.builder()
                .conversationId(conversationId)
                .status(RunStatus.QUEUED)
                .version(1)
                .build();
        run = runRepository.save(run);

        Message message = Message.builder()
                .conversationId(conversationId)
                .runId(run.getId())
                .role("user")
                .content(request.getText())
                .clientMessageId(request.getClientMessageId())
                .sequenceNo(nextSeq)
                .build();
        message = messageRepository.save(message);

        return new SendMessageResponse(conversationId, run.getId(), message.getId());
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(UUID conversationId, UserPrincipal principal, Pageable pageable) {
        conversationRepository.findByIdAndOwnerId(conversationId, principal.userId())
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        return messageRepository.findByConversationIdOrderBySequenceNoAsc(conversationId, pageable)
                .map(this::mapMessageToResponse);
    }

    private ConversationResponse mapToResponse(Conversation c) {
        return new ConversationResponse(c.getId(), c.getOwnerId(), c.getTitle(), c.getActiveTaskId(), c.getCreatedAt(), c.getUpdatedAt());
    }

    private MessageResponse mapMessageToResponse(Message m) {
        return new MessageResponse(m.getId(), m.getConversationId(), m.getRunId(), m.getRole(), m.getContent(),
                m.getToolCallId(), m.getToolName(), m.getToolArguments(), m.getToolResult(),
                m.getClientMessageId(), m.getSequenceNo(), m.getCreatedAt());
    }
}
```

Create `ChatController.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request.CreateConversationRequest;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.request.SendMessageRequest;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.ConversationResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.MessageResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response.SendMessageResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.security.UserPrincipal;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.ConversationService;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai/conversations")
@RequiredArgsConstructor
public class ChatController {

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ConversationResponse> createConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestBody(required = false) CreateConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(conversationService.createConversation(principal, request));
    }

    @GetMapping
    public ResponseEntity<Page<ConversationResponse>> listConversations(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(conversationService.listConversations(principal, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ConversationResponse> getConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        return ResponseEntity.ok(conversationService.getConversation(principal, id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConversation(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id) {
        conversationService.deleteConversation(principal, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/messages")
    public ResponseEntity<SendMessageResponse> sendMessage(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(conversationService.sendMessage(id, principal, request));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<Page<MessageResponse>> getMessages(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ResponseEntity.ok(conversationService.getMessages(id, principal, pageable));
    }
}
```

Create `RunController.java` with GET `/api/v1/ai/runs/{id}` and POST `/api/v1/ai/runs/{id}/cancel`.
Create `OperationController.java` with GET `/api/v1/ai/operations/{id}`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ChatControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/controller/ ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/ConversationService.java ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/controller/ChatControllerTest.java
git commit -m "feat(ai-agent): add Chat, Run, and Operation REST controllers"
```

---

### Task 7: SSE Event Broadcaster & RunEvent Streaming

**Files:**
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/SseBroadcaster.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/controller/RunController.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/SseBroadcasterTest.java`

**Interfaces:**
- Consumes: `RunEventRepository` for event persistence.
- Produces: `SseEmitter subscribe(UUID runId)` and `void emit(UUID runId, String eventType, Object payload)`.
- HTTP Endpoint: `GET /api/v1/ai/runs/{id}/events` -> `text/event-stream`.

- [ ] **Step 1: Write the failing test for SseBroadcaster**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/SseBroadcasterTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SseBroadcasterTest {

    @Test
    void testSubscribeAndEmit() {
        SseBroadcaster broadcaster = new SseBroadcaster();
        UUID runId = UUID.randomUUID();

        SseEmitter emitter = broadcaster.subscribe(runId);
        assertNotNull(emitter);

        broadcaster.emit(runId, "assistant.delta", "Hello world");
        broadcaster.complete(runId);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=SseBroadcasterTest`
Expected: FAIL (SseBroadcaster class does not exist).

- [ ] **Step 3: Implement SseBroadcaster and wire into RunController**

Create `SseBroadcaster.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class SseBroadcaster {

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID runId) {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 minutes timeout
        emitters.computeIfAbsent(runId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(runId, emitter));
        emitter.onTimeout(() -> removeEmitter(runId, emitter));
        emitter.onError(e -> removeEmitter(runId, emitter));

        try {
            emitter.send(SseEmitter.event().name("connect").data("connected"));
        } catch (IOException e) {
            removeEmitter(runId, emitter);
        }
        return emitter;
    }

    public void emit(UUID runId, String eventType, Object payload) {
        List<SseEmitter> list = emitters.get(runId);
        if (list == null || list.isEmpty()) return;

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event().name(eventType).data(payload));
            } catch (Exception e) {
                removeEmitter(runId, emitter);
            }
        }
    }

    public void complete(UUID runId) {
        List<SseEmitter> list = emitters.remove(runId);
        if (list != null) {
            for (SseEmitter emitter : list) {
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        }
    }

    private void removeEmitter(UUID runId, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(runId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(runId);
            }
        }
    }
}
```

Add endpoint to `RunController.java`:
```java
    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribeEvents(@PathVariable UUID id) {
        return sseBroadcaster.subscribe(id);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=SseBroadcasterTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/SseBroadcaster.java ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/controller/RunController.java ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/SseBroadcasterTest.java
git commit -m "feat(ai-agent): implement SseBroadcaster for streaming run events"
```

---

### Task 8: ContextBuilder & System Prompt Construction

**Files:**
- Create:
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilder.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/TokenEstimator.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilderTest.java`

**Interfaces:**
- Consumes: `Conversation`, `Task`, `MessageRepository`, `AgentProperties`.
- Produces: `List<ChatMessage>` (formatted system, history, task state) ready for LLM invocation.

- [ ] **Step 1: Write the failing test for ContextBuilder**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilderTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationSummaryRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ContextBuilderTest {

    @Test
    void testBuildPromptContainsRoleAndRules() {
        MessageRepository messageRepo = mock(MessageRepository.class);
        ConversationSummaryRepository summaryRepo = mock(ConversationSummaryRepository.class);
        AgentProperties properties = new AgentProperties();

        ContextBuilder builder = new ContextBuilder(messageRepo, summaryRepo, properties);

        Conversation conv = Conversation.builder().id(UUID.randomUUID()).build();
        when(messageRepo.findByConversationIdOrderBySequenceNoAsc(conv.getId())).thenReturn(List.of(
                Message.builder().role("user").content("Tìm lớp của tôi").sequenceNo(1).build()
        ));

        String prompt = builder.buildSystemPrompt("ROLE_STUDENT", null);
        assertTrue(prompt.contains("WGS"));
        assertTrue(prompt.contains("ROLE_STUDENT"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ContextBuilderTest`
Expected: FAIL (ContextBuilder class does not exist).

- [ ] **Step 3: Implement TokenEstimator and ContextBuilder**

Create `TokenEstimator.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.springframework.stereotype.Component;

@Component
public class TokenEstimator {
    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) return 0;
        // Conservative heuristic: ~4 characters per token in English, ~2-3 chars for Vietnamese utf-8
        return (int) Math.ceil(text.length() / 3.0);
    }
}
```

Create `ContextBuilder.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Conversation;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.task.Task;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.ConversationSummaryRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ContextBuilder {

    private final MessageRepository messageRepository;
    private final ConversationSummaryRepository summaryRepository;
    private final AgentProperties agentProperties;

    public String buildSystemPrompt(String role, Task task) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là AI Assistant thông minh của hệ thống Web Grading System (WGS).\n");
        sb.append("Vai trò của người dùng hiện tại: ").append(role).append(".\n\n");
        sb.append("QUY TẮC BẮT BUỘC:\n");
        sb.append("1. Trả lời bằng tiếng Việt, thân thiện, súc tích và chính xác.\n");
        sb.append("2. Sử dụng tool được cung cấp để tra cứu dữ liệu. Tuyệt đối không tự bịa đặt thông tin database.\n");
        sb.append("3. Không bao giờ tiết lộ mã nguồn ẩn, test case ẩn hoặc đáp án chấm cho sinh viên.\n");
        sb.append("4. Đối với giảng viên tạo bài tập: hỏi lại các tham số cần thiết, đề xuất cấu hình bài tập trong text và chỉ thực hiện tạo khi người dùng đồng ý.\n");
        sb.append("5. Không tuyên bố thao tác đã hoàn thành khi chưa nhận được kết quả thành công từ tool.\n");

        if (task != null && task.getStateJson() != null) {
            sb.append("\nTRẠNG THÁI NHIỆM VỤ HIỆN TẠI:\n").append(task.getStateJson()).append("\n");
        }
        return sb.toString();
    }

    public List<Message> getRecentMessages(Conversation conversation) {
        int windowSize = agentProperties.getContext().getHistoryWindowSize();
        List<Message> allMessages = messageRepository.findByConversationIdOrderBySequenceNoAsc(conversation.getId());
        if (allMessages.size() <= windowSize) {
            return allMessages;
        }
        return allMessages.subList(allMessages.size() - windowSize, allMessages.size());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ContextBuilderTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilder.java ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/TokenEstimator.java ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilderTest.java
git commit -m "feat(ai-agent): implement ContextBuilder and TokenEstimator"
```

---

### Task 9: GuardChecker & RunWorker Async Loop

**Files:**
- Create:
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/GuardChecker.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/RunWorker.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentOrchestrator.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/GuardCheckerTest.java`

**Interfaces:**
- Consumes: `Run`, `AgentProperties`.
- Produces: Validation checks (step count, elapsed time, cancellation flag) and background execution of QUEUED runs.

- [ ] **Step 1: Write the failing test for GuardChecker**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/GuardCheckerTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GuardCheckerTest {

    @Test
    void testGuardCheckerLimits() {
        AgentProperties props = new AgentProperties();
        props.getLimits().setMaxModelSteps(5);
        props.getLimits().setActiveTimeoutSeconds(60);

        GuardChecker checker = new GuardChecker(props);

        Run run = Run.builder()
                .status(RunStatus.RUNNING)
                .startedAt(Instant.now().minusSeconds(10))
                .build();

        assertTrue(checker.canContinue(run, 3));
        assertFalse(checker.canContinue(run, 5), "Should halt when max steps reached");

        run.setStatus(RunStatus.CANCELLED);
        assertFalse(checker.canContinue(run, 1), "Should halt when run cancelled");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=GuardCheckerTest`
Expected: FAIL (GuardChecker does not exist).

- [ ] **Step 3: Implement GuardChecker, AgentOrchestrator, and RunWorker**

Create `GuardChecker.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.orchestrator;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.config.AgentProperties;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.Run;
import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;

import java.time.Duration;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class GuardChecker {

    private final AgentProperties properties;

    public boolean canContinue(Run run, int currentSteps) {
        if (run.getStatus() == RunStatus.CANCELLED) return false;
        if (currentSteps >= properties.getLimits().getMaxModelSteps()) return false;

        if (run.getStartedAt() != null) {
            long elapsed = Duration.between(run.getStartedAt(), Instant.now()).getSeconds();
            if (elapsed >= properties.getLimits().getActiveTimeoutSeconds()) {
                return false;
            }
        }
        return true;
    }
}
```

Create `AgentOrchestrator.java` to manage the multi-step reasoning loop (calling Spring AI ChatModel, processing tool calls, persisting message checkpoints, emitting SSE events).
Create `RunWorker.java` with `@Async` or executor-based run processing triggered when runs enter `QUEUED` state.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=GuardCheckerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/GuardCheckerTest.java
git commit -m "feat(ai-agent): implement GuardChecker and agent loop worker"
```

---

## Phase 3: Tools System

### Task 10: Tool Infrastructure Core

**Files:**
- Create:
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/EffectType.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolDefinition.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolResult.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/AgentContext.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/AgentTool.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolRegistry.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolExecutor.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolRegistryTest.java`

**Interfaces:**
- Produces: Contract for all agent tools, permission filtering by user role, arguments validation, sanitization.

- [ ] **Step 1: Write the failing test for ToolRegistry**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolRegistryTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ToolRegistryTest {

    @Test
    void testFilterToolsByRole() {
        AgentTool studentTool = new AgentTool() {
            @Override public String getName() { return "studentTool"; }
            @Override public String getDescription() { return "Student only"; }
            @Override public ToolDefinition getDefinition() {
                return new ToolDefinition("studentTool", "Student only", "1.0", "student", "read", "sub",
                        EffectType.READ, Set.of("ROLE_STUDENT"), Duration.ofSeconds(5), 10000);
            }
            @Override public ToolResult execute(JsonNode args, AgentContext ctx) { return ToolResult.ok("ok"); }
        };

        AgentTool lecturerTool = new AgentTool() {
            @Override public String getName() { return "lecturerTool"; }
            @Override public String getDescription() { return "Lecturer only"; }
            @Override public ToolDefinition getDefinition() {
                return new ToolDefinition("lecturerTool", "Lecturer only", "1.0", "lecturer", "write", "ass",
                        EffectType.DOMAIN_WRITE, Set.of("ROLE_LECTURER"), Duration.ofSeconds(5), 10000);
            }
            @Override public ToolResult execute(JsonNode args, AgentContext ctx) { return ToolResult.ok("ok"); }
        };

        ToolRegistry registry = new ToolRegistry(List.of(studentTool, lecturerTool));

        List<AgentTool> forStudent = registry.getToolsForRoles(Set.of("ROLE_STUDENT"));
        assertEquals(1, forStudent.size());
        assertEquals("studentTool", forStudent.getFirst().getName());

        List<AgentTool> forLecturer = registry.getToolsForRoles(Set.of("ROLE_LECTURER"));
        assertEquals(1, forLecturer.size());
        assertEquals("lecturerTool", forLecturer.getFirst().getName());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ToolRegistryTest`
Expected: FAIL (Interfaces and classes do not exist).

- [ ] **Step 3: Implement Tool interfaces, definitions, and registry**

Create `EffectType.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

public enum EffectType {
    READ,
    TASK_MODIFY,
    DOMAIN_WRITE
}
```

Create `ToolDefinition.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import java.time.Duration;
import java.util.Set;

public record ToolDefinition(
        String name,
        String description,
        String schemaVersion,
        String capabilityGroup,
        String action,
        String resourceType,
        EffectType effectType,
        Set<String> requiredRoles,
        Duration timeout,
        int outputLimitBytes
) {}
```

Create `ToolResult.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {
    private boolean success;
    private String code;
    private boolean retryable;
    private String message;
    private Object data;
    private String correlationId;

    public static ToolResult ok(Object data) {
        return ToolResult.builder().success(true).code("OK").retryable(false).data(data).build();
    }

    public static ToolResult error(String code, String message, boolean retryable) {
        return ToolResult.builder().success(false).code(code).message(message).retryable(retryable).build();
    }
}
```

Create `AgentContext.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import java.util.Set;
import java.util.UUID;

public record AgentContext(
        UUID userId,
        String email,
        Set<String> roles,
        UUID conversationId,
        UUID runId,
        String gatewaySecret
) {}
```

Create `AgentTool.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import com.fasterxml.jackson.databind.JsonNode;

public interface AgentTool {
    String getName();
    String getDescription();
    ToolDefinition getDefinition();
    ToolResult execute(JsonNode arguments, AgentContext context);
}
```

Create `ToolRegistry.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.tools;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class ToolRegistry {

    private final Map<String, AgentTool> toolsByName = new HashMap<>();

    public ToolRegistry(List<AgentTool> tools) {
        for (AgentTool tool : tools) {
            toolsByName.put(tool.getName(), tool);
        }
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    public List<AgentTool> getToolsForRoles(Set<String> userRoles) {
        return toolsByName.values().stream()
                .filter(t -> t.getDefinition().requiredRoles().stream().anyMatch(userRoles::contains))
                .collect(Collectors.toList());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ToolRegistryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolRegistryTest.java
git commit -m "feat(ai-agent): implement AgentTool interface, registry, and execution contracts"
```

---

### Task 11: Feign Client Extensions & SubmissionServiceClient

**Files:**
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/client/SubmissionServiceClient.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/client/CourseServiceClient.java`
- Create: Inter-service DTOs in `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/client/dto/`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/client/FeignContractTest.java`

**Interfaces:**
- Consumes: Endpoints on `course-service` (8081) and `submission-service` (8082).
- Produces: Feign clients for listing classes, assignments, and retrieving submissions.

- [ ] **Step 1: Write the failing test for Feign client contracts**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/client/FeignContractTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
public class FeignContractTest {

    @MockitoBean
    private SubmissionServiceClient submissionServiceClient;

    @MockitoBean
    private CourseServiceClient courseServiceClient;

    @Test
    void testFeignClientsConfigured() {
        assertNotNull(submissionServiceClient);
        assertNotNull(courseServiceClient);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=FeignContractTest`
Expected: FAIL (SubmissionServiceClient does not exist).

- [ ] **Step 3: Implement SubmissionServiceClient and extend CourseServiceClient**

Create `SubmissionServiceClient.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ApiEnvelope;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.PageDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.SubmissionSummaryDto;

import java.util.UUID;

@FeignClient(name = "submission-service", url = "${feign.submission-service.url:http://localhost:8082}")
public interface SubmissionServiceClient {

    @GetMapping("/api/v1/submissions")
    PageDto<SubmissionSummaryDto> listMySubmissions(
            @RequestHeader("X-User-Id") String studentId,
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestHeader("X-Gateway-Secret") String gatewaySecret
    );

    @GetMapping("/api/v1/submissions/{id}")
    SubmissionSummaryDto getSubmission(
            @PathVariable("id") UUID id,
            @RequestHeader("X-User-Id") String studentId,
            @RequestHeader("X-Gateway-Secret") String gatewaySecret
    );
}
```

Add lecturer and student query endpoints to `CourseServiceClient.java` (`listMyClasses`, `listAssignments`, `createAssignment`).

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=FeignContractTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/client/ ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/client/FeignContractTest.java
git commit -m "feat(ai-agent): add SubmissionServiceClient and extend CourseServiceClient"
```

---

### Task 12: Common Agent Tools (Classes & Assignments)

**Files:**
- Create:
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/common/ListMyClassesTool.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/common/ListVisibleAssignmentsTool.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/common/GetAssignmentTool.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/common/CommonToolsTest.java`

**Interfaces:**
- Consumes: `CourseServiceClient`, `AgentContext`.
- Produces: `ToolResult` containing list of classes, assignments, and detailed assignment requirements.

- [ ] **Step 1: Write the failing test for common tools**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/common/CommonToolsTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.common;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolResult;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

public class CommonToolsTest {

    @Test
    void testListMyClassesToolExecutes() {
        CourseServiceClient client = mock(CourseServiceClient.class);
        ListMyClassesTool tool = new ListMyClassesTool(client);

        AgentContext ctx = new AgentContext(UUID.randomUUID(), "test@ptit.edu.vn", Set.of("ROLE_STUDENT"), UUID.randomUUID(), UUID.randomUUID(), "secret");
        ToolResult result = tool.execute(null, ctx);
        assertTrue(result.isSuccess());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=CommonToolsTest`
Expected: FAIL (ListMyClassesTool does not exist).

- [ ] **Step 3: Implement ListMyClassesTool, ListVisibleAssignmentsTool, GetAssignmentTool**

Create `ListMyClassesTool.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.common;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ListMyClassesTool implements AgentTool {

    private final CourseServiceClient courseServiceClient;

    @Override
    public String getName() {
        return "listMyClasses";
    }

    @Override
    public String getDescription() {
        return "Lấy danh sách các lớp học mà người dùng hiện tại tham gia hoặc phụ trách giảng dạy.";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "listMyClasses",
                getDescription(),
                "1.0",
                "common",
                "read",
                "class",
                EffectType.READ,
                Set.of("ROLE_STUDENT", "ROLE_LECTURER"),
                Duration.ofSeconds(5),
                10000
        );
    }

    @Override
    public ToolResult execute(JsonNode arguments, AgentContext context) {
        try {
            // Forward user identity headers to course-service
            var response = courseServiceClient.listClassesForUser(
                    context.userId().toString(),
                    String.join(",", context.roles()),
                    context.gatewaySecret()
            );
            return ToolResult.ok(response != null ? response.getData() : List.of());
        } catch (Exception e) {
            log.error("Failed to execute listMyClasses for user {}", context.userId(), e);
            return ToolResult.error("COURSE_SERVICE_ERROR", "Không thể lấy danh sách lớp học: " + e.getMessage(), true);
        }
    }
}
```

Implement `ListVisibleAssignmentsTool.java` and `GetAssignmentTool.java` similarly.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=CommonToolsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/common/ ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/common/CommonToolsTest.java
git commit -m "feat(ai-agent): implement common agent tools for classes and assignments"
```

---

### Task 13: Student Agent Tools

**Files:**
- Create:
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/student/GetMySubmissionTool.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/student/GetMyVisibleResultTool.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/student/RecommendAssignmentsTool.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/student/ListMyPendingAssignmentsTool.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/student/StudentToolsTest.java`

**Interfaces:**
- Consumes: `SubmissionServiceClient`, `ResultServiceClient`, `CourseServiceClient`.
- Produces: Student-scoped queries with privacy filters (sanitizing hidden tests and solutions).

- [ ] **Step 1: Write the failing test for student tools**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/student/StudentToolsTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.student;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.SubmissionServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolResult;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

public class StudentToolsTest {

    @Test
    void testGetMySubmissionRejectsLecturerRole() {
        SubmissionServiceClient client = mock(SubmissionServiceClient.class);
        GetMySubmissionTool tool = new GetMySubmissionTool(client);

        AgentContext lecturerCtx = new AgentContext(UUID.randomUUID(), "lecturer@ptit.edu.vn", Set.of("ROLE_LECTURER"), UUID.randomUUID(), UUID.randomUUID(), "secret");
        ToolResult result = tool.execute(null, lecturerCtx);
        assertFalse(result.isSuccess());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=StudentToolsTest`
Expected: FAIL (GetMySubmissionTool does not exist).

- [ ] **Step 3: Implement student tools**

Create `GetMySubmissionTool.java` with role verification and submission fetching.
Create `GetMyVisibleResultTool.java` using `ResultServiceClient.getResultsForStudent`.
Create `RecommendAssignmentsTool.java` and `ListMyPendingAssignmentsTool.java`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=StudentToolsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/student/ ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/student/StudentToolsTest.java
git commit -m "feat(ai-agent): implement student query tools"
```

---

### Task 14: Lecturer Query Tools

**Files:**
- Create:
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/GetTestStepCapabilitiesTool.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/ListAllowedImagesTool.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/ListClassStudentsTool.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/GetSubmissionsForAssignmentTool.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/LecturerQueryToolsTest.java`

**Interfaces:**
- Produces: Capabilities inspection (`HTTP_REQUEST`, `DB_QUERY`, `DB_SCHEMA_CHECK`, `DB_MIGRATION`), image whitelist (`openjdk:21`, `postgres:16`, `python:3.11`, etc.), and class student roster inspection.

- [ ] **Step 1: Write the failing test for lecturer query tools**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/LecturerQueryToolsTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.lecturer;

import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolResult;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class LecturerQueryToolsTest {

    @Test
    void testGetTestStepCapabilitiesReturnsSupportedTypes() {
        GetTestStepCapabilitiesTool tool = new GetTestStepCapabilitiesTool();
        AgentContext ctx = new AgentContext(UUID.randomUUID(), "lec@ptit.edu.vn", Set.of("ROLE_LECTURER"), UUID.randomUUID(), UUID.randomUUID(), "secret");
        ToolResult result = tool.execute(null, ctx);

        assertTrue(result.isSuccess());
        List<?> steps = (List<?>) result.getData();
        assertTrue(steps.contains("HTTP_REQUEST"));
        assertTrue(steps.contains("DB_QUERY"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=LecturerQueryToolsTest`
Expected: FAIL (GetTestStepCapabilitiesTool does not exist).

- [ ] **Step 3: Implement lecturer query tools**

Create `GetTestStepCapabilitiesTool.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.lecturer;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.*;

import java.time.Duration;
import java.util.List;
import java.util.Set;

@Component
public class GetTestStepCapabilitiesTool implements AgentTool {

    @Override
    public String getName() {
        return "getTestStepCapabilities";
    }

    @Override
    public String getDescription() {
        return "Trả về danh sách 4 loại test step được hệ thống WGS hỗ trợ và định dạng schema của chúng.";
    }

    @Override
    public ToolDefinition getDefinition() {
        return new ToolDefinition(
                "getTestStepCapabilities",
                getDescription(),
                "1.0",
                "lecturer",
                "read",
                "capabilities",
                EffectType.READ,
                Set.of("ROLE_LECTURER"),
                Duration.ofSeconds(3),
                5000
        );
    }

    @Override
    public ToolResult execute(JsonNode arguments, AgentContext context) {
        return ToolResult.ok(List.of(
                "HTTP_REQUEST",
                "DB_QUERY",
                "DB_SCHEMA_CHECK",
                "DB_MIGRATION"
        ));
    }
}
```

Implement `ListAllowedImagesTool.java`, `ListClassStudentsTool.java`, and `GetSubmissionsForAssignmentTool.java`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=LecturerQueryToolsTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/ ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/LecturerQueryToolsTest.java
git commit -m "feat(ai-agent): implement lecturer query tools"
```

---

### Task 15: Lecturer Creation Tool & Idempotent Command Execution

**Files:**
- Create:
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/OperationService.java`
  - `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/CreateAssignmentTool.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/CreateAssignmentToolTest.java`

**Interfaces:**
- Consumes: `CourseServiceClient.createAssignment`, `OperationRepository`.
- Produces: `ToolResult` with created assignment ID, protected by idempotency key check.

- [ ] **Step 1: Write the failing test for CreateAssignmentTool**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/CreateAssignmentToolTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.tools.lecturer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.CourseServiceClient;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.OperationService;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.AgentContext;
import vn.edu.ptit.web_grading_system.ai_assistant_service.tools.ToolResult;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

public class CreateAssignmentToolTest {

    @Test
    void testValidateFailsWhenRequiredFieldsMissing() throws Exception {
        CourseServiceClient client = mock(CourseServiceClient.class);
        OperationService opService = mock(OperationService.class);
        CreateAssignmentTool tool = new CreateAssignmentTool(client, opService, new ObjectMapper());

        AgentContext ctx = new AgentContext(UUID.randomUUID(), "lec@ptit.edu.vn", Set.of("ROLE_LECTURER"), UUID.randomUUID(), UUID.randomUUID(), "secret");
        ToolResult result = tool.execute(new ObjectMapper().readTree("{}"), ctx);

        assertFalse(result.isSuccess(), "Should fail when title and classId missing");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=CreateAssignmentToolTest`
Expected: FAIL (CreateAssignmentTool does not exist).

- [ ] **Step 3: Implement OperationService and CreateAssignmentTool**

Create `OperationService.java` for idempotency management.
Create `CreateAssignmentTool.java` with validation:
- Validates `title` (not blank)
- Validates `classId` (UUID)
- Validates test plan steps against the 4 supported types
- Generates `idempotencyKey`
- Calls `CourseServiceClient.createAssignment(...)`
- Records result in `ai_operation`

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=CreateAssignmentToolTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/OperationService.java ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/CreateAssignmentTool.java ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/CreateAssignmentToolTest.java
git commit -m "feat(ai-agent): implement CreateAssignmentTool with idempotency protection"
```

---

## Phase 4: Frontend Chat Drawer & Integration

### Task 16: Frontend API Client & Types

**Files:**
- Create:
  - `web-grading-system-fe/src/shared/types/aiAgent.ts`
  - `web-grading-system-fe/src/shared/api/endpoints/ai.ts`
- Test: `npm run build` / `npm run lint`

**Interfaces:**
- Produces: Strongly typed functions `createConversation()`, `listConversations()`, `sendMessage()`, `getMessages()`, `getRunStatus()`, `cancelRun()`, `subscribeRunEvents()`.

- [ ] **Step 1: Write types file**

Create `web-grading-system-fe/src/shared/types/aiAgent.ts`:
```typescript
export type RunStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'WAITING_USER'
  | 'WAITING_APPROVAL'
  | 'SUCCEEDED'
  | 'FAILED'
  | 'CANCELLED'
  | 'LIMIT_REACHED'

export interface ConversationResponse {
  id: string
  ownerId: string
  title: string
  activeTaskId: string | null
  createdAt: string
  updatedAt: string
}

export interface MessageResponse {
  id: string
  conversationId: string
  runId: string | null
  role: 'user' | 'assistant' | 'tool' | 'system'
  content: string | null
  toolCallId: string | null
  toolName: string | null
  toolArguments: string | null
  toolResult: string | null
  clientMessageId: string | null
  sequenceNo: number
  createdAt: string
}

export interface SendMessageRequest {
  text: string
  clientMessageId?: string
  expectedTaskVersion?: number
}

export interface SendMessageResponse {
  conversationId: string
  runId: string
  messageId: string
}

export interface RunResponse {
  id: string
  conversationId: string
  status: RunStatus
  errorCode?: string | null
  errorMessage?: string | null
  startedAt?: string | null
  finishedAt?: string | null
}
```

- [ ] **Step 2: Write API endpoint wrapper**

Create `web-grading-system-fe/src/shared/api/endpoints/ai.ts`:
```typescript
import { getData, sendData } from '../http'
import type {
  ConversationResponse,
  MessageResponse,
  SendMessageRequest,
  SendMessageResponse,
  RunResponse,
} from '../../types/aiAgent'

export function createConversation(title?: string): Promise<ConversationResponse> {
  return sendData<ConversationResponse, { title?: string }>('/api/v1/ai/conversations', 'post', { title })
}

export function listConversations(page = 0, size = 20): Promise<{ content: ConversationResponse[] }> {
  return getData<{ content: ConversationResponse[] }>('/api/v1/ai/conversations', { page, size })
}

export function sendMessage(
  conversationId: string,
  request: SendMessageRequest,
): Promise<SendMessageResponse> {
  return sendData<SendMessageResponse, SendMessageRequest>(
    `/api/v1/ai/conversations/${conversationId}/messages`,
    'post',
    request,
  )
}

export function getMessages(
  conversationId: string,
  page = 0,
  size = 50,
): Promise<{ content: MessageResponse[] }> {
  return getData<{ content: MessageResponse[] }>(
    `/api/v1/ai/conversations/${conversationId}/messages`,
    { page, size },
  )
}

export function getRunStatus(runId: string): Promise<RunResponse> {
  return getData<RunResponse>(`/api/v1/ai/runs/${runId}`)
}

export function cancelRun(runId: string): Promise<void> {
  return sendData<void, undefined>(`/api/v1/ai/runs/${runId}/cancel`, 'post')
}

export function subscribeRunEvents(
  runId: string,
  onMessage: (event: MessageEvent) => void,
  onError?: (err: Event) => void,
): () => void {
  const eventSource = new EventSource(`/api/v1/ai/runs/${runId}/events`)
  eventSource.onmessage = onMessage
  if (onError) eventSource.onerror = onError
  return () => eventSource.close()
}
```

- [ ] **Step 3: Verify TypeScript build**

Run: `npm run build` in `web-grading-system-fe`
Expected: PASS without type errors.

- [ ] **Step 4: Commit**

```bash
git add src/shared/types/aiAgent.ts src/shared/api/endpoints/ai.ts
git commit -m "feat(fe): add AI agent types and API endpoint client"
```

---

### Task 17: React Query Hooks for AI Chat

**Files:**
- Create:
  - `web-grading-system-fe/src/features/ai-chat/hooks/useConversations.ts`
  - `web-grading-system-fe/src/features/ai-chat/hooks/useMessages.ts`
  - `web-grading-system-fe/src/features/ai-chat/hooks/useSendMessage.ts`
  - `web-grading-system-fe/src/features/ai-chat/hooks/useRunEvents.ts`
- Test: `npm run lint`

**Interfaces:**
- Consumes: `ai.ts` API endpoints.
- Produces: TanStack React Query custom hooks for seamless state caching and live mutations.

- [ ] **Step 1: Implement hooks**

Create `useConversations.ts`:
```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { listConversations, createConversation } from '../../../shared/api/endpoints/ai'

export function useConversations() {
  const queryClient = useQueryClient()

  const query = useQuery({
    queryKey: ['ai-conversations'],
    queryFn: () => listConversations(0, 30),
  })

  const createMutation = useMutation({
    mutationFn: (title?: string) => createConversation(title),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ai-conversations'] })
    },
  })

  return {
    conversations: query.data?.content ?? [],
    isLoading: query.isLoading,
    createConversation: createMutation.mutateAsync,
  }
}
```

Implement `useMessages.ts`, `useSendMessage.ts`, and `useRunEvents.ts` (subscribing to SSE on `runId` and updating the message stream buffer).

- [ ] **Step 2: Verify with linting**

Run: `npm run lint` in `web-grading-system-fe`
Expected: 0 errors.

- [ ] **Step 3: Commit**

```bash
git add src/features/ai-chat/hooks/
git commit -m "feat(fe): implement React Query hooks for conversations and messages"
```

---

### Task 18: Message Display & Tool Cards Components

**Files:**
- Create:
  - `web-grading-system-fe/src/features/ai-chat/components/UserMessage.tsx`
  - `web-grading-system-fe/src/features/ai-chat/components/AssistantMessage.tsx`
  - `web-grading-system-fe/src/features/ai-chat/components/ToolExecutionCard.tsx`
  - `web-grading-system-fe/src/features/ai-chat/components/StreamingIndicator.tsx`
- Test: `npm run build`

**Interfaces:**
- Produces: Ant Design-styled chat bubbles with avatar, timestamp, markdown rendering, and collapsible tool execution logs.

- [ ] **Step 1: Implement components**

Create `UserMessage.tsx`:
```tsx
import React from 'react'
import { Typography } from 'antd'
import { colors } from '../../../shared/theme/tokens'

interface Props {
  content: string
  createdAt?: string
}

export const UserMessage: React.FC<Props> = ({ content }) => {
  return (
    <div style={{ display: 'flex', justifyContent: 'flex-end', marginBottom: 12 }}>
      <div
        style={{
          maxWidth: '80%',
          background: colors.primary,
          color: '#fff',
          padding: '10px 14px',
          borderRadius: '14px 14px 2px 14px',
          fontSize: 14,
          lineHeight: '1.5',
          wordBreak: 'break-word',
        }}
      >
        <Typography.Text style={{ color: '#fff' }}>{content}</Typography.Text>
      </div>
    </div>
  )
}
```

Create `AssistantMessage.tsx`, `ToolExecutionCard.tsx`, and `StreamingIndicator.tsx`.

- [ ] **Step 2: Verify with build**

Run: `npm run build`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/features/ai-chat/components/
git commit -m "feat(fe): implement message display and tool execution card components"
```

---

### Task 19: Chat Controls & Header Status Components

**Files:**
- Create:
  - `web-grading-system-fe/src/features/ai-chat/components/ChatInput.tsx`
  - `web-grading-system-fe/src/features/ai-chat/components/ConversationSelector.tsx`
  - `web-grading-system-fe/src/features/ai-chat/components/RunStatusBar.tsx`
- Test: `npm run build`

**Interfaces:**
- Produces: Chat input with Enter/Shift+Enter key handling, conversation switcher dropdown, and run status indicator.

- [ ] **Step 1: Implement components**

Create `ChatInput.tsx` with textarea, Send button, Enter-to-send support.
Create `ConversationSelector.tsx` with Ant Design Select / Dropdown.
Create `RunStatusBar.tsx` displaying current run status (QUEUED, RUNNING, WAITING_USER).

- [ ] **Step 2: Verify with build**

Run: `npm run build`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/features/ai-chat/components/ChatInput.tsx src/features/ai-chat/components/ConversationSelector.tsx src/features/ai-chat/components/RunStatusBar.tsx
git commit -m "feat(fe): implement chat input, conversation selector, and run status bar"
```

---

### Task 20: AiChatSidebar & AppLayout Shell Integration

**Files:**
- Create: `web-grading-system-fe/src/features/ai-chat/AiChatSidebar.tsx`
- Modify: `web-grading-system-fe/src/shared/layout/AppLayout.tsx`
- Test: `npm run build` && `npm run lint`

**Interfaces:**
- Produces: Toggleable Drawer (380px) on the right side of the screen, triggered via an AI Copilot button in the AppLayout Header.

- [ ] **Step 1: Implement AiChatSidebar**

Create `web-grading-system-fe/src/features/ai-chat/AiChatSidebar.tsx`:
```tsx
import React, { useState } from 'react'
import { Drawer } from 'antd'
import { ConversationSelector } from './components/ConversationSelector'
import { RunStatusBar } from './components/RunStatusBar'
import { UserMessage } from './components/UserMessage'
import { AssistantMessage } from './components/AssistantMessage'
import { StreamingIndicator } from './components/StreamingIndicator'
import { ChatInput } from './components/ChatInput'
import { useConversations } from './hooks/useConversations'
import { useMessages } from './hooks/useMessages'
import { useSendMessage } from './hooks/useSendMessage'
import { useRunEvents } from './hooks/useRunEvents'

interface Props {
  open: boolean
  onClose: () => void
}

export const AiChatSidebar: React.FC<Props> = ({ open, onClose }) => {
  const { conversations, createConversation } = useConversations()
  const [selectedConvId, setSelectedConvId] = useState<string | null>(null)

  const activeConvId = selectedConvId ?? (conversations[0]?.id ?? null)
  const { messages, isLoading } = useMessages(activeConvId)
  const { sendMessage, isSending } = useSendMessage()
  const { streamingText, activeRunStatus } = useRunEvents(activeConvId)

  const handleSend = async (text: string) => {
    let targetConvId = activeConvId
    if (!targetConvId) {
      const newConv = await createConversation('Hội thoại mới')
      targetConvId = newConv.id
      setSelectedConvId(targetConvId)
    }
    await sendMessage({ conversationId: targetConvId, text })
  }

  return (
    <Drawer
      title="🤖 Trợ lý AI WGS"
      placement="right"
      width={400}
      onClose={onClose}
      open={open}
      styles={{ body: { padding: 0, display: 'flex', flexDirection: 'column' } }}
    >
      <div style={{ padding: '8px 16px', borderBottom: '1px solid #f0f0f0' }}>
        <ConversationSelector
          conversations={conversations}
          activeId={activeConvId}
          onSelect={setSelectedConvId}
          onNew={() => createConversation('Hội thoại mới').then(c => setSelectedConvId(c.id))}
        />
        <RunStatusBar status={activeRunStatus} />
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: 16 }}>
        {messages.map((m) =>
          m.role === 'user' ? (
            <UserMessage key={m.id} content={m.content ?? ''} createdAt={m.createdAt} />
          ) : (
            <AssistantMessage key={m.id} content={m.content ?? ''} createdAt={m.createdAt} />
          )
        )}
        {streamingText && <AssistantMessage content={streamingText} isStreaming />}
        {isSending && <StreamingIndicator />}
      </div>

      <ChatInput onSend={handleSend} disabled={isSending} />
    </Drawer>
  )
}
```

- [ ] **Step 2: Add AI Assistant toggle to AppLayout header**

In `web-grading-system-fe/src/shared/layout/AppLayout.tsx`:
Add state `const [aiChatOpen, setAiChatOpen] = useState(false)` and an AI toggle button in the Header:
```tsx
<Button
  type="primary"
  ghost
  icon={<RobotOutlined />}
  onClick={() => setAiChatOpen(true)}
>
  Trợ lý AI
</Button>
<AiChatSidebar open={aiChatOpen} onClose={() => setAiChatOpen(false)} />
```

- [ ] **Step 3: Verify build and lint**

Run: `npm run build && npm run lint` in `web-grading-system-fe`
Expected: PASS with 0 errors.

- [ ] **Step 4: Commit**

```bash
git add src/features/ai-chat/ src/shared/layout/AppLayout.tsx
git commit -m "feat(fe): integrate AiChatSidebar drawer into AppLayout header"
```

---

## Phase 5: End-to-End Verification

### Task 21: Full System Build & Acceptance Verification

**Files:**
- Backend: All files in `ai-assistant-service`
- Frontend: All files in `web-grading-system-fe`

**Interfaces:**
- Verifies all 10 acceptance scenarios from the design spec:
  1. Conversational assignment creation
  2. Permission isolation
  3. Hidden test sanitization
  4. Role-based tool access control
  5. Idempotent operations
  6. Failure recovery
  7. Loop limits
  8. Message deduplication
  9. Context summarization
  10. Clean cancellation

- [ ] **Step 1: Run complete backend test suite**

Run in `ai-assistant-service`:
`mvn clean test`
Expected: All unit and integration tests PASS.

- [ ] **Step 2: Run complete frontend build and lint suite**

Run in `web-grading-system-fe`:
`npm run lint && npm run build`
Expected: 0 errors, production build succeeds.

- [ ] **Step 3: Commit and tag completion**

```bash
git commit --allow-empty -m "chore(ai-agent): full integration verification passed"
```

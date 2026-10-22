# Spring AI Refactoring Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `ai-assistant-service` to use native Spring AI 2.0.0 abstractions (Tool Calling with JSON schema, JPA-backed Chat Memory, Structured Output with `ChatClient.entity()`, and real-time SSE token streaming) instead of custom boilerplate code.

**Architecture:** Replace custom tool calling, message concatenating, manual JSON parsing, and blocking SSE with Spring AI `ChatClient`, `FunctionToolCallback`, `MessageChatMemoryAdvisor` (backed by a custom `JpaChatMemory`), and `ChatClient.prompt().stream().content()`.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring AI 2.0.0 (`spring-ai-starter-model-openai`), PostgreSQL, Feign Client, JUnit 5, Mockito.

## Global Constraints
- Preserve all existing REST endpoints, database schema migrations, and frontend SSE event contract formats (`tool.started`, `tool.completed`, `assistant.delta`, `run.completed`, `run.failed`).
- Maintain offline fallback capability for unit tests and local development without requiring external OpenAI keys.
- Role-based tool access control (`ROLE_STUDENT` vs `ROLE_LECTURER`) must be strictly enforced before tool execution.
- All tests in `mvn test` must pass after every task.

---

### Task 1: JPA-backed Chat Memory Implementation (`JpaChatMemory`)

**Files:**
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/memory/JpaChatMemory.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/memory/JpaChatMemoryTest.java`

**Interfaces:**
- Consumes: `vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository`
- Produces: `org.springframework.ai.chat.memory.ChatMemory` bean for injection into `ChatClient` Advisors.

- [ ] **Step 1: Write the failing unit test**

Create `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/memory/JpaChatMemoryTest.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JpaChatMemoryTest {

    @Mock
    private MessageRepository messageRepository;

    private JpaChatMemory chatMemory;

    @BeforeEach
    void setUp() {
        chatMemory = new JpaChatMemory(messageRepository);
    }

    @Test
    void testAddAndGetMessages() {
        UUID conversationId = UUID.randomUUID();
        when(messageRepository.findMaxSequenceNo(conversationId)).thenReturn(0);

        List<Message> newMessages = List.of(
                new UserMessage("Xin chào!"),
                new AssistantMessage("Chào bạn, tôi có thể giúp gì?")
        );

        chatMemory.add(conversationId.toString(), newMessages);

        verify(messageRepository, times(2)).save(any(vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.class));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl ai-assistant-service -Dtest=JpaChatMemoryTest`
Expected: Compilation failure or FAIL (class `JpaChatMemory` does not exist yet).

- [ ] **Step 3: Implement `JpaChatMemory`**

Create `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/memory/JpaChatMemory.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.memory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.web_grading_system.ai_assistant_service.repository.MessageRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JpaChatMemory implements ChatMemory {

    private final MessageRepository messageRepository;

    @Override
    @Transactional
    public void add(String conversationId, List<Message> messages) {
        if (conversationId == null || messages == null || messages.isEmpty()) {
            return;
        }
        try {
            UUID convId = UUID.fromString(conversationId);
            int seq = messageRepository.findMaxSequenceNo(convId);
            for (Message msg : messages) {
                seq++;
                String role = switch (msg.getMessageType()) {
                    case USER -> "user";
                    case ASSISTANT -> "assistant";
                    case SYSTEM -> "system";
                    default -> "tool";
                };
                vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message entity =
                        vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message.builder()
                                .conversationId(convId)
                                .role(role)
                                .content(msg.getText())
                                .sequenceNo(seq)
                                .build();
                messageRepository.save(entity);
            }
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID for conversationId in ChatMemory: {}", conversationId);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Message> get(String conversationId) {
        if (conversationId == null) {
            return Collections.emptyList();
        }
        try {
            UUID convId = UUID.fromString(conversationId);
            List<vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation.Message> dbMessages =
                    messageRepository.findByConversationIdOrderBySequenceNoAsc(convId);

            List<Message> result = new ArrayList<>();
            for (var dbMsg : dbMessages) {
                String content = dbMsg.getContent() != null ? dbMsg.getContent() : "";
                if ("user".equalsIgnoreCase(dbMsg.getRole())) {
                    result.add(new UserMessage(content));
                } else if ("assistant".equalsIgnoreCase(dbMsg.getRole())) {
                    result.add(new AssistantMessage(content));
                } else if ("system".equalsIgnoreCase(dbMsg.getRole())) {
                    result.add(new SystemMessage(content));
                }
            }
            return result;
        } catch (IllegalArgumentException e) {
            log.warn("Invalid UUID for conversationId in ChatMemory: {}", conversationId);
            return Collections.emptyList();
        }
    }

    @Override
    @Transactional
    public void clear(String conversationId) {
        // Conversation messages are retained for auditing; no-op or mark soft-deleted if needed.
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl ai-assistant-service -Dtest=JpaChatMemoryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/memory/JpaChatMemory.java ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/memory/JpaChatMemoryTest.java
git commit -m "feat(ai): implement JpaChatMemory backed by MessageRepository"
```

---

### Task 2: Tool Schema Generation & Spring AI ToolCallback Registration

**Files:**
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/AgentTool.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolRegistry.java`
- Modify: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolRegistryTest.java`

**Interfaces:**
- Consumes: `AgentTool` beans, `AgentContext`
- Produces: `List<ToolCallback>` with populated `ToolDefinition.inputSchema` for `ChatClient.prompt().toolCallbacks(...)`.

- [ ] **Step 1: Write unit test in `ToolRegistryTest` verifying ToolCallback schema**

Add test method to `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolRegistryTest.java`:
```java
    @Test
    void getToolCallbacksForRoles_shouldReturnExecutableToolCallbacks() {
        AgentContext context = new AgentContext(UUID.randomUUID(), "test@ptit.edu.vn", Set.of("ROLE_LECTURER"), "127.0.0.1", null, "secret");
        List<org.springframework.ai.tool.ToolCallback> callbacks = registry.getToolCallbacksForRoles(Set.of("ROLE_LECTURER"), context);

        assertNotNull(callbacks);
        assertFalse(callbacks.isEmpty());
        org.springframework.ai.tool.ToolCallback cb = callbacks.get(0);
        assertNotNull(cb.getToolDefinition().getName());
        assertNotNull(cb.getToolDefinition().getDescription());
    }
```

- [ ] **Step 2: Run test to verify failure**

Run: `mvn test -pl ai-assistant-service -Dtest=ToolRegistryTest`
Expected: FAIL (cannot resolve method `getToolCallbacksForRoles`).

- [ ] **Step 3: Update `AgentTool.java` and `ToolRegistry.java`**

In `AgentTool.java`, allow tools to declare an input class for automatic schema resolution:
```java
    default Class<?> getInputType() {
        return null;
    }
```

In `ToolRegistry.java`, add `getToolCallbacksForRoles(Set<String> userRoles, AgentContext context)`:
```java
    public List<org.springframework.ai.tool.ToolCallback> getToolCallbacksForRoles(Set<String> userRoles, AgentContext context) {
        List<AgentTool> authorizedTools = getToolsForRoles(userRoles);
        List<org.springframework.ai.tool.ToolCallback> callbacks = new ArrayList<>();
        for (AgentTool tool : authorizedTools) {
            callbacks.add(new org.springframework.ai.tool.ToolCallback() {
                @Override
                public org.springframework.ai.tool.definition.ToolDefinition getToolDefinition() {
                    return tool.getToolDefinition();
                }

                @Override
                public String call(String toolInput) {
                    return call(toolInput, null);
                }

                @Override
                public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        com.fasterxml.jackson.databind.JsonNode args = (toolInput != null && !toolInput.isBlank())
                                ? mapper.readTree(toolInput)
                                : mapper.createObjectNode();
                        ToolResult res = tool.execute(args, context);
                        return res != null && res.getData() != null ? mapper.writeValueAsString(res.getData()) : "{}";
                    } catch (Exception e) {
                        return "{\"error\":\"" + e.getMessage() + "\"}";
                    }
                }
            });
        }
        return callbacks;
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl ai-assistant-service -Dtest=ToolRegistryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolRegistry.java ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/AgentTool.java ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolRegistryTest.java
git commit -m "feat(ai): support Spring AI ToolCallback conversion with AgentContext in ToolRegistry"
```

---

### Task 3: Structured Output for `AiExplanationService` via Spring AI

**Files:**
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/AiExplanationResultDto.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/AiExplanationService.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/LlmClient.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/AiExplanationServiceTest.java`

**Interfaces:**
- Consumes: `ChatClient`, `PromptBuilder`
- Produces: `AiExplanationResultDto` strongly-typed result without manual Gson parsing or regex fence stripping.

- [ ] **Step 1: Create `AiExplanationResultDto.java`**

Create `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/AiExplanationResultDto.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.dto;

import java.util.List;

public record AiExplanationResultDto(
        String overallSummary,
        List<StepAnalysis> stepAnalyses
) {}
```

- [ ] **Step 2: Update `LlmClient` to expose typed entity completion**

In `LlmClient.java`, add:
```java
    public <T> T generateEntity(String systemPrompt, String userPrompt, Class<T> entityClass) {
        if (isOfflineMode()) {
            String fallback = generateFallbackResponse(userPrompt);
            return gson.fromJson(fallback, entityClass);
        }
        try {
            return chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .entity(entityClass);
        } catch (Exception e) {
            log.error("Failed to generate structured entity via Spring AI: {}", e.getMessage(), e);
            String fallback = generateFallbackResponse(userPrompt);
            return gson.fromJson(fallback, entityClass);
        }
    }
```

- [ ] **Step 3: Update `AiExplanationService.java` to use `generateEntity`**

In `AiExplanationService.java`, replace lines 272-293 with:
```java
        AiExplanationResultDto structuredOutput = llmClient.generateEntity(systemPrompt, userPrompt, AiExplanationResultDto.class);
        String overallSummary = structuredOutput != null && structuredOutput.overallSummary() != null
                ? structuredOutput.overallSummary()
                : "Bài nộp đã được phân tích toàn diện.";
        String diagnosis = overallSummary;
        List<StepAnalysis> stepAnalyses = (structuredOutput != null && structuredOutput.stepAnalyses() != null)
                ? structuredOutput.stepAnalyses()
                : new ArrayList<>();
```

- [ ] **Step 4: Run `AiExplanationServiceTest` to verify it passes**

Run: `mvn test -pl ai-assistant-service -Dtest=AiExplanationServiceTest`
Expected: PASS (all 14 tests pass)

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/AiExplanationResultDto.java ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/LlmClient.java ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/AiExplanationService.java
git commit -m "refactor(ai): use Spring AI ChatClient structured output for AiExplanationService"
```

---

### Task 4: Agent Orchestrator Spring AI Integration (Tool Calling, Chat Memory & Streaming)

**Files:**
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentOrchestrator.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilder.java`
- Modify: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilderTest.java`

**Interfaces:**
- Consumes: `ChatClient`, `ToolRegistry`, `JpaChatMemory`, `MessageChatMemoryAdvisor`, `SseBroadcaster`
- Produces: Fully functional agent loop with tool execution logging in `ai_tool_execution` and SSE token streaming.

- [ ] **Step 1: Update `AgentOrchestrator` to inject `ToolRegistry`, `JpaChatMemory`, and `ChatClient`**

In `AgentOrchestrator.java`:
1. Inject `ToolRegistry toolRegistry`, `JpaChatMemory jpaChatMemory`, and `ChatClient chatClient`.
2. Construct `AgentContext` for the conversation owner.
3. Retrieve authorized tool callbacks via `toolRegistry.getToolCallbacksForRoles(roles, agentContext)`.
4. Use `MessageChatMemoryAdvisor.builder(jpaChatMemory).build()` configured with `conversationId`.
5. Invoke `chatClient.prompt().system(...).advisors(...).toolCallbacks(...).stream().content()` to stream tokens via SSE and capture the complete assistant response.
6. Persist assistant message in DB and complete the run.

- [ ] **Step 2: Run unit and integration tests**

Run: `mvn test -pl ai-assistant-service`
Expected: PASS (all tests pass)

- [ ] **Step 3: Commit**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentOrchestrator.java ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilder.java
git commit -m "feat(ai): integrate Spring AI ChatClient, ToolCallbacks, and ChatMemory in AgentOrchestrator"
```

---

### Task 5: Full Verification & Clean Build

**Files:**
- Verify: All files in `ai-assistant-service`

- [ ] **Step 1: Run full Maven test suite**

Run: `mvn clean test -pl ai-assistant-service`
Expected: BUILD SUCCESS with 0 failures, 0 errors.

- [ ] **Step 2: Check git status to ensure working directory is clean**

Run: `git status`
Expected: Clean working tree.

- [ ] **Step 3: Verification commit**

```bash
git commit --allow-empty -m "chore(ai): verify Spring AI refactoring across ai-assistant-service"
```

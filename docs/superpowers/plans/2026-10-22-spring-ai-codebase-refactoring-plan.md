# Spring AI Codebase Refactoring & Redundancy Removal Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor custom, redundant implementations in `ai-assistant-service` and standardize on Spring AI 2.0.0 built-ins: external template resource in `PromptBuilder`, `BeanOutputConverter` in `AiExplanationService`, and `MessageChatMemoryAdvisor` with `JpaChatMemory` in `AgentOrchestrator` while removing dead code in `ContextBuilder`.

**Architecture:** Use Spring AI `SystemPromptTemplate` / `PromptTemplate` backed by `classpath:/prompts/pedagogical-tutor-system.st` for clean prompt isolation; integrate Spring AI `BeanOutputConverter<AiExplanationResultDto>` for type-safe LLM structured output parsing; integrate `MessageChatMemoryAdvisor` into `LlmClient.streamChatWithMemory` backed by `JpaChatMemory` and remove manual database message polling/saving boilerplate in `AgentOrchestrator` and `ContextBuilder`.

**Tech Stack:** Java 21, Spring Boot 4.1.0, Spring AI 2.0.0 (`spring-ai-client-chat`, `spring-ai-starter-model-openai`), JUnit 5, Mockito.

## Global Constraints
- Preserve existing external REST API contracts (`/api/v1/ai/runs/**`, `/api/v1/ai/conversations/**`, `/api/v1/ai/explanations/**`).
- Do not break student ownership or lecturer authority checks.
- Keep realistic offline fallback responses working when no LLM API key is present.
- All 51+ unit and integration tests must pass cleanly at every stage.

---

### Task 1: Prompt Templating with External `.st` Resource

**Files:**
- Create: `ai-assistant-service/src/main/resources/prompts/pedagogical-tutor-system.st`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/PromptBuilder.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/AiExplanationServiceTest.java`

**Interfaces:**
- Consumes: `org.springframework.core.io.Resource`, `org.springframework.ai.chat.prompt.SystemPromptTemplate`
- Produces: `PromptBuilder.buildSystemPrompt()` returning rendered String from resource.

- [ ] **Step 1: Create the prompt template resource file**

Create `ai-assistant-service/src/main/resources/prompts/pedagogical-tutor-system.st`:
```text
Bạn là Trợ giảng AI Thông minh (Pedagogical AI Tutor) chuyên ngành Công nghệ Thông tin tại Học viện Công nghệ Bưu chính Viễn thông (PTIT).
Nhiệm vụ của bạn là đánh giá và phân tích bài nộp của sinh viên nhằm nâng cao kỹ năng lập trình và thiết kế phần mềm.

QUY TẮC CỐT LÕI:
1. PHƯƠNG PHÁP SƯ PHẠM: TUYỆT ĐỐI KHÔNG viết sẵn toàn bộ code sửa lỗi cho sinh viên chép. Hãy đóng vai người hướng dẫn: chỉ rõ nguyên nhân gốc rễ, vị trí nghi vấn trong code, đưa ra các gợi ý từng bước (Hints) và từ khóa tự học (Learning Keywords) để sinh viên tự sửa và hiểu sâu.
2. VỚI CÁC STEP THẤT BẠI (FAIL):
   - category: "DEBUG_FAIL"
   - Chẩn đoán chính xác nguyên nhân (dựa trên status code, response body, error message, stack trace).
   - Đưa ra vị trí nghi ngờ (suspected location, ví dụ: Controller thiếu @Valid, Service thiếu check null, Entity thiếu annotation).
   - Đưa ra danh sách gợi ý hành động cụ thể (actionable hints).
3. VỚI CÁC STEP THÀNH CÔNG (PASS):
   - category: "OPTIMIZE_PASS"
   - Không chỉ khen ngợi, hãy rà soát 4 tiêu chí tối ưu:
     a) N+1 Query: Kiểm tra dbQueryCount và dbQueryLogs. Nếu API lấy danh sách mà có quá nhiều câu SELECT đơn lẻ -> cảnh báo N+1 Query.
     b) Over-fetching / Bảo mật: Kiểm tra response JSON có để lộ trường nhạy cảm hoặc thừa thãi không.
     c) Chuẩn RESTful: Đánh giá mã HTTP phản hồi (ví dụ: POST tạo mới nên trả về 201 Created thay vì 200 OK).
     d) Hiệu năng & Bộ nhớ: Đối chiếu duration_ms và peak_memory_mb.
4. ĐỊNH DẠNG ĐẦU RA:
   - Bạn BẮT BUỘC chỉ trả về định dạng JSON hợp lệ, KHÔNG thêm markdown backticks (```json ... ```) ở đầu/cuối, tuân thủ đúng cấu trúc sau:
   {
     "overallSummary": "Tóm tắt tổng quan bài nộp...",
     "stepAnalyses": [
       {
         "stepId": "<uuid>",
         "stepName": "<tên step>",
         "stepType": "<loại step>",
         "passed": true/false,
         "category": "DEBUG_FAIL" hoặc "OPTIMIZE_PASS",
         "diagnosis": "Chẩn đoán chi tiết...",
         "suspectedLocation": "Vị trí nghi vấn...",
         "actionableHints": ["Gợi ý 1", "Gợi ý 2"],
         "learningKeywords": ["Từ khóa 1", "Từ khóa 2"],
         "telemetryAudit": {
           "durationMs": 120,
           "expectedThresholdMs": 200,
           "nPlusOneDetected": false,
           "queryCount": 1,
           "sensitiveFieldsExposed": [],
           "peakMemoryMb": 64
         }
       }
     ]
   }
5. NGÔN NGỮ: Tiếng Việt sư phạm, chuyên nghiệp, khích lệ tinh thần học tập của sinh viên.
```

- [ ] **Step 2: Update `PromptBuilder` to render system prompt from resource**

Update `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/PromptBuilder.java`:
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.AssignmentDetailDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.ResultDto;
import vn.edu.ptit.web_grading_system.ai_assistant_service.client.dto.StepResultDto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class PromptBuilder {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Resource systemPromptResource;

    public PromptBuilder(@Value("classpath:/prompts/pedagogical-tutor-system.st") Resource systemPromptResource) {
        this.systemPromptResource = systemPromptResource;
    }

    public String buildSystemPrompt() {
        return new SystemPromptTemplate(systemPromptResource).render();
    }

    public String buildUserPrompt(AssignmentDetailDto assignment, ResultDto result) {
        Map<String, Object> context = new HashMap<>();
        context.put("assignmentTitle", assignment != null ? assignment.getTitle() : "N/A");
        context.put("assignmentDescription", assignment != null ? assignment.getDescription() : "N/A");
        context.put("maxMemoryMb", assignment != null ? assignment.getMaxMemoryMb() : null);
        context.put("executionTimeoutMs", assignment != null ? assignment.getExecutionTimeoutMs() : null);

        context.put("submissionScore", result.getScore());
        context.put("maxScore", result.getMaxScore());
        context.put("status", result.getStatus());
        context.put("summaryLog", result.getSummaryLog());

        List<StepResultDto> steps = result.getSteps() != null ? result.getSteps() : List.of();
        List<Map<String, Object>> sanitizedSteps = steps.stream().map(s -> {
            Map<String, Object> map = new HashMap<>();
            map.put("stepId", s.getStepId());
            map.put("stepName", s.getStepName());
            map.put("stepType", s.getStepType());
            map.put("passed", s.getPassed());
            map.put("skipped", s.getSkipped());
            map.put("durationMs", s.getDurationMs());
            map.put("responseStatus", s.getResponseStatus());
            map.put("expectedValue", s.getExpectedValue());
            map.put("actualValue", DataSanitizer.sanitizeBody(s.getActualValue()));
            map.put("errorMessage", s.getErrorMessage());
            map.put("dbQueryCount", s.getDbQueryCount());
            map.put("dbQueryLogs", s.getDbQueryLogs());
            map.put("stackTrace", s.getStackTrace());
            map.put("peakMemoryMb", s.getPeakMemoryMb());
            return map;
        }).toList();

        context.put("steps", sanitizedSteps);

        return "Hãy phân tích toàn diện bài nộp dưới đây theo các chỉ dẫn và xuất ra JSON:\n" + gson.toJson(context);
    }

    public org.springframework.ai.chat.prompt.Prompt buildPrompt(AssignmentDetailDto assignment, ResultDto result) {
        return new org.springframework.ai.chat.prompt.Prompt(java.util.List.of(
                new org.springframework.ai.chat.messages.SystemMessage(buildSystemPrompt()),
                new org.springframework.ai.chat.messages.UserMessage(buildUserPrompt(assignment, result))
        ));
    }
}
```

- [ ] **Step 3: Run AiExplanationServiceTest to verify PromptBuilder changes**

Run command:
```bash
mvn test -Dtest=AiExplanationServiceTest
```
Expected output: Tests run: 14, Failures: 0, Errors: 0, BUILD SUCCESS.

- [ ] **Step 4: Commit Task 1**

```bash
git add ai-assistant-service/src/main/resources/prompts/pedagogical-tutor-system.st ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/PromptBuilder.java
git commit -m "refactor(ai): externalize pedagogical system prompt template into resource file"
```

---

### Task 2: BeanOutputConverter in `AiExplanationService`

**Files:**
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/AiExplanationService.java:273-290`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/AiExplanationServiceTest.java`

**Interfaces:**
- Consumes: `org.springframework.ai.converter.BeanOutputConverter`
- Produces: `structuredOutput` parsed safely via `BeanOutputConverter<AiExplanationResultDto>`

- [ ] **Step 1: Refactor `AiExplanationService` fallback to use `BeanOutputConverter`**

In `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/AiExplanationService.java`, add the converter field:
```java
    private final org.springframework.ai.converter.BeanOutputConverter<AiExplanationResultDto> outputConverter =
            new org.springframework.ai.converter.BeanOutputConverter<>(AiExplanationResultDto.class);
```
And replace lines 273-290:
```java
        AiExplanationResultDto structuredOutput = llmClient.generateEntity(systemPrompt, userPrompt, AiExplanationResultDto.class);
        if (structuredOutput == null) {
            String rawLlmResponse = llmClient.generateCompletion(systemPrompt, userPrompt);
            if (rawLlmResponse != null && !rawLlmResponse.isBlank()) {
                try {
                    structuredOutput = outputConverter.convert(rawLlmResponse);
                } catch (Exception e) {
                    log.warn("BeanOutputConverter could not parse LLM fallback response: {}", e.getMessage());
                }
            }
        }
```

- [ ] **Step 2: Run test suite for `AiExplanationServiceTest`**

Run command:
```bash
mvn test -Dtest=AiExplanationServiceTest
```
Expected output: Tests run: 14, Failures: 0, Errors: 0, BUILD SUCCESS.

- [ ] **Step 3: Commit Task 2**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/AiExplanationService.java
git commit -m "refactor(ai): use Spring AI BeanOutputConverter for explanation fallback parsing"
```

---

### Task 3: Chat Memory Integration (`MessageChatMemoryAdvisor`) & Orchestrator Cleanup

**Files:**
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/LlmClient.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentOrchestrator.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilder.java`
- Modify: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilderTest.java`
- Modify: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentOrchestratorTest.java`

**Interfaces:**
- Consumes: `org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor`, `org.springframework.ai.chat.memory.ChatMemory`
- Produces: `LlmClient.streamChatWithMemory(...)`, cleaner `ContextBuilder`, streamlined `AgentOrchestrator.executeRun`

- [ ] **Step 1: Add `streamChatWithMemory` to `LlmClient`**

In `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/LlmClient.java`, add:
```java
    public Flux<String> streamChatWithMemory(
            String systemPrompt,
            String conversationId,
            org.springframework.ai.chat.memory.ChatMemory chatMemory,
            List<ToolCallback> toolCallbacks
    ) {
        if (isOfflineMode()) {
            List<org.springframework.ai.chat.messages.Message> history = chatMemory != null
                    ? chatMemory.get(conversationId) : List.of();
            String fallbackPrompt = (history != null && !history.isEmpty())
                    ? history.get(history.size() - 1).getText() : "";
            return Flux.just(generateFallbackResponse(fallbackPrompt));
        }
        try {
            var spec = chatClient.prompt().system(systemPrompt);
            if (chatMemory != null && conversationId != null) {
                spec.advisors(org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor.builder(chatMemory).build());
                spec.advisors(a -> a.param("chat_memory_conversation_id", conversationId));
            }
            if (toolCallbacks != null && !toolCallbacks.isEmpty()) {
                spec.toolCallbacks(toolCallbacks);
            }
            return spec.stream().content()
                    .filter(s -> s != null && !s.isEmpty());
        } catch (Exception e) {
            log.error("Failed to stream chat with memory via Spring AI: {}", e.getMessage(), e);
            return Flux.just(generateFallbackResponse(""));
        }
    }
```

- [ ] **Step 2: Streamline `ContextBuilder` by removing redundant methods**

In `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilder.java`:
- Remove `buildSpringAiMessages()` (redundant with `JpaChatMemory.get()`).
- Remove `buildUserPromptWithHistory()` (redundant with multi-turn messages).
- Remove `getRecentMessages()` (redundant with ChatMemory advisor).
- The class retains `buildSystemPrompt(String role, Task task)` using `SystemPromptTemplate`.

- [ ] **Step 3: Update `AgentOrchestrator` to use `streamChatWithMemory`**

In `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentOrchestrator.java`:
- Use `jpaChatMemory` directly.
- Replace lines 135-188 with:
```java
                String systemPrompt = contextBuilder.buildSystemPrompt("ROLE_USER", task);

                AgentContext agentContext = new AgentContext(
                        conv.getOwnerId(),
                        "",
                        Set.of("ROLE_STUDENT", "ROLE_LECTURER"),
                        conv.getId(),
                        runId,
                        gatewayProperties != null ? gatewayProperties.secret() : ""
                );

                List<ToolCallback> toolCallbacks = (toolRegistry != null)
                        ? toolRegistry.getToolCallbacksForRoles(agentContext.roles(), agentContext)
                        : List.of();

                StringBuilder responseBuffer = new StringBuilder();
                Flux<String> stream = (llmClient != null)
                        ? llmClient.streamChatWithMemory(systemPrompt, conv.getId().toString(), jpaChatMemory, toolCallbacks)
                        : null;

                if (stream != null) {
                    try {
                        stream.doOnNext(delta -> {
                            responseBuffer.append(delta);
                            sseBroadcaster.sendDelta(runId, delta);
                        }).blockLast();
                    } catch (Exception ex) {
                        log.warn("Streaming encountered error: {}", ex.getMessage());
                    }
                }

                String completion = responseBuffer.toString().trim();
                if (completion.isEmpty() && llmClient != null) {
                    List<org.springframework.ai.chat.messages.Message> history = (jpaChatMemory != null)
                            ? jpaChatMemory.get(conv.getId().toString()) : List.of();
                    String lastUserText = history != null && !history.isEmpty()
                            ? history.get(history.size() - 1).getText() : "";
                    completion = llmClient.generateCompletion(systemPrompt, lastUserText);
                    sseBroadcaster.sendDelta(runId, completion);
                }

                // If memory advisor was not running in offline mode, persist assistant message into jpaChatMemory
                if (jpaChatMemory != null) {
                    jpaChatMemory.add(conv.getId().toString(), List.of(new org.springframework.ai.chat.messages.AssistantMessage(completion)));
                }

                completed = true;
```

- [ ] **Step 4: Update `ContextBuilderTest` and `AgentOrchestratorTest`**

- In `ContextBuilderTest.java`: test `buildSystemPrompt` and `TokenEstimator`.
- In `AgentOrchestratorTest.java`: inject mock `JpaChatMemory` and update verification accordingly.

- [ ] **Step 5: Run unit tests for Orchestrator & ContextBuilder**

Run command:
```bash
mvn test -Dtest=ContextBuilderTest,AgentOrchestratorTest,JpaChatMemoryTest
```
Expected output: Tests run, Failures: 0, Errors: 0, BUILD SUCCESS.

- [ ] **Step 6: Commit Task 3**

```bash
git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/LlmClient.java ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentOrchestrator.java ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilder.java ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilderTest.java ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentOrchestratorTest.java
git commit -m "refactor(ai): integrate MessageChatMemoryAdvisor and eliminate redundant orchestrator methods"
```

---

### Task 4: Full System Verification & Regression Suite

**Files:**
- Test all: `ai-assistant-service/src/test/**`

- [ ] **Step 1: Execute full maven test suite across `ai-assistant-service`**

Run command:
```bash
mvn clean test
```
Expected output:
- `Tests run: 51, Failures: 0, Errors: 0, Skipped: 0`
- `BUILD SUCCESS`

- [ ] **Step 2: Commit final refactoring validation**

```bash
git status
```
Ensure working tree is clean and all tests succeed.

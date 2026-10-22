# Spring AI Codebase Refactoring & Redundancy Removal Design

- **Date:** 2026-10-22
- **Module:** `ai-assistant-service`
- **Goal:** Refactor custom, redundant implementations and adopt standard Spring AI 2.0.0 classes and mechanisms (`MessageChatMemoryAdvisor`, `BeanOutputConverter`, `PromptTemplate`), removing manual boilerplate code while preserving full functionality, security, and passing all automated tests.

---

## 1. Problem Statement & Motivation

During the initial assessment of the `ai-assistant-service`, several areas were identified where custom boilerplate code replicates built-in functionality of Spring AI:

1. **Manual Conversation Memory Orchestration in `AgentOrchestrator` & `ContextBuilder`**:
   - `AgentOrchestrator` manually queries `MessageRepository`, delegates to `ContextBuilder.buildSpringAiMessages` to convert entities to `UserMessage`/`AssistantMessage`, and manually saves new `Message` entities to the database after completion.
   - `JpaChatMemory` already implements `org.springframework.ai.chat.memory.ChatMemory`, but was not yet wired into the LLM streaming execution path via an advisor.
   - Methods `ContextBuilder.buildSpringAiMessages()`, `ContextBuilder.buildUserPromptWithHistory()`, and `ContextBuilder.getRecentMessages()` are redundant with `ChatMemory` and Spring AI's multi-turn conversational capabilities.

2. **Manual JSON Parsing Fallback in `AiExplanationService`**:
   - In `AiExplanationService.generate()`, if `llmClient.generateEntity(...)` returns null, the code manually parses raw JSON using Gson `JsonParser`, iterates through `stepAnalyses` elements, and extracts properties by hand.
   - Spring AI's `BeanOutputConverter<T>` provides schema generation, code-fence trimming, and type-safe deserialization out of the box.

3. **Hardcoded Prompt Strings in `PromptBuilder`**:
   - `PromptBuilder` hardcodes a 60+ line system prompt string in Java code and builds user prompts through manual JSON map serialization.
   - Spring AI provides `PromptTemplate` and `SystemPromptTemplate` with external resource support (`.st` files), separating prompt engineering from Java application logic.

---

## 2. Proposed Architecture & Design

### Component 1: Conversation Memory Integration via `MessageChatMemoryAdvisor`

#### Design
- In [`LlmClient`](file:///d:/Project/DATN/web-grading-system-backend-services/ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/LlmClient.java):
  Add `streamChatWithMemory(String systemPrompt, String conversationId, ChatMemory chatMemory, List<ToolCallback> toolCallbacks)` that configures `ChatClient` with `MessageChatMemoryAdvisor`:
  ```java
  public Flux<String> streamChatWithMemory(
          String systemPrompt,
          String conversationId,
          ChatMemory chatMemory,
          List<ToolCallback> toolCallbacks
  ) {
      if (isOfflineMode()) {
          List<org.springframework.ai.chat.messages.Message> history = chatMemory.get(conversationId);
          String fallbackPrompt = history != null && !history.isEmpty()
                  ? history.get(history.size() - 1).getText() : "";
          return Flux.just(generateFallbackResponse(fallbackPrompt));
      }

      var spec = chatClient.prompt()
              .system(systemPrompt)
              .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
              .advisors(a -> a.param("chat_memory_conversation_id", conversationId));

      if (toolCallbacks != null && !toolCallbacks.isEmpty()) {
          spec.toolCallbacks(toolCallbacks);
      }

      return spec.stream().content()
              .filter(s -> s != null && !s.isEmpty());
  }
  ```

- In [`AgentOrchestrator`](file:///d:/Project/DATN/web-grading-system-backend-services/ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentOrchestrator.java):
  - Delegate conversation history retrieval and message persistence to `MessageChatMemoryAdvisor` and `JpaChatMemory`.
  - When `MessageChatMemoryAdvisor` is active during stream completion, Spring AI retrieves conversation context and persists the incoming assistant response into `JpaChatMemory`.
  - In `JpaChatMemory`, ensure that `add(...)` and `get(...)` correctly assign sequence numbers and roles for seamless compatibility.

- In [`ContextBuilder`](file:///d:/Project/DATN/web-grading-system-backend-services/ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilder.java):
  - Remove redundant methods: `buildSpringAiMessages()`, `buildUserPromptWithHistory()`, `getRecentMessages()`.
  - Retain `buildSystemPrompt(String role, Task task)` using `SystemPromptTemplate`.

---

### Component 2: Fallback Output Parsing via `BeanOutputConverter`

#### Design
- In [`AiExplanationService`](file:///d:/Project/DATN/web-grading-system-backend-services/ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/AiExplanationService.java):
  - Replace the manual `JsonObject` / `JsonParser` block with `BeanOutputConverter<AiExplanationResultDto>`:
  ```java
  private final BeanOutputConverter<AiExplanationResultDto> outputConverter =
          new BeanOutputConverter<>(AiExplanationResultDto.class);

  // In generate():
  if (structuredOutput == null) {
      String rawLlmResponse = llmClient.generateCompletion(systemPrompt, userPrompt);
      if (rawLlmResponse != null && !rawLlmResponse.isBlank()) {
          try {
              structuredOutput = outputConverter.convert(rawLlmResponse);
          } catch (Exception e) {
              log.warn("BeanOutputConverter could not parse LLM response: {}", e.getMessage());
          }
      }
  }
  ```
- Eliminates 20+ lines of fragile JSON parsing and unifies parsing on Spring AI's converter.

---

### Component 3: Prompt Templating via External `.st` Resource

#### Design
- Create template file:
  `ai-assistant-service/src/main/resources/prompts/pedagogical-tutor-system.st`
  containing the PTIT pedagogical tutor system instructions.
- In [`PromptBuilder`](file:///d:/Project/DATN/web-grading-system-backend-services/ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/PromptBuilder.java):
  - Inject the system prompt resource via `@Value("classpath:/prompts/pedagogical-tutor-system.st") private Resource systemPromptResource;`.
  - Render with `new SystemPromptTemplate(systemPromptResource).render()`.
  - `buildPrompt(assignment, result)` returns `Prompt` with `SystemMessage` and `UserMessage`.

---

## 3. Non-Functional Requirements & Safety Constraints

1. **API Backward Compatibility**: All REST endpoints (`/api/v1/ai/runs/**`, `/api/v1/ai/conversations/**`, `/api/v1/ai/explanations/**`) must maintain identical request/response formats.
2. **Security & Authorization**: Student ownership checks and Lecturer class ownership checks remain untouched.
3. **Offline / Fallback Resilience**: When running without an OpenAI API key (offline dev / CI tests), realistic fallback pedagogical explanations and chat responses must continue to function.
4. **Test Pass Guarantee**: All existing unit and integration tests must pass cleanly.

---

## 4. Verification Plan

- `mvn clean test` across `ai-assistant-service` to verify:
  - `AiExplanationServiceTest` (14 tests)
  - `ContextBuilderTest`
  - `ToolRegistryTest`
  - `JpaChatMemoryTest`
  - `LlmClientTest`
  - `AgentOrchestratorTest` / Run execution flows
- Target: 51/51 tests passing with 0 failures, 0 errors.

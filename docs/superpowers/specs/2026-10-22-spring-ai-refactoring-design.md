# Spring AI Refactoring Design Specification

> **Date**: 2026-10-22  
> **Status**: Approved  
> **Target Service**: `ai-assistant-service`  
> **Spring AI Version**: 2.0.0 (Spring Boot 4.1.0)  

---

## 1. Mục tiêu & Bối cảnh

### Mục tiêu
Thay thế các đoạn mã tự code thủ công (reinventing the wheel) trong `ai-assistant-service` bằng các abstraction, class và method chuẩn được cung cấp bởi **Spring AI 2.0.0**:
1. **Tool Calling & Execution**: Tự động sinh JSON Schema cho Tool và kích hoạt vòng lặp gọi tool trong Orchestrator thông qua Spring AI `ChatClient.prompt().tools(...)`.
2. **Chat Memory**: Triển khai `org.springframework.ai.chat.memory.ChatMemory` đọc/ghi trực tiếp từ bảng `ai_message`, kết hợp `MessageChatMemoryAdvisor` để quản lý sliding window và ngữ cảnh hội thoại chuẩn thay cho việc ghép chuỗi text thô.
3. **Structured Output**: Sử dụng `ChatClient.prompt().call().entity(...)` / `BeanOutputConverter` cho `AiExplanationService`, loại bỏ các đoạn bóc tách markdown backticks regex và parse JSON bằng Google Gson thủ công.
4. **Real-time Streaming**: Tận dụng `ChatClient.prompt().stream().content()` (`Flux<String>`) để đẩy token từng phần qua Server-Sent Events (SSE).

### Ràng buộc
- **Zero Breaking Changes**: Không sửa đổi REST API endpoint contracts, database schema migrations, và format của các SSE event gửi tới frontend.
- **Role-based Access Control**: Giữ nguyên cơ chế phân quyền thực thi tool theo vai trò (`ROLE_STUDENT`, `ROLE_LECTURER`).
- **Offline / Test Fallback**: Đảm bảo chế độ offline fallback và các bộ unit/integration test hiện có tiếp tục pass.

---

## 2. Kiến trúc & Thiết kế chi tiết

### 2.1. Tool Calling & Schema Generation

#### Hiện trạng
- `AgentTool` hardcode `inputSchema("{}")`.
- `ToolExecutor` và `ToolRegistry` tự quản lý map và gọi tool, nhưng chưa được gắn vào `AgentOrchestrator`.

#### Giải pháp Spring AI
- Cung cấp schema class hoặc annotation metadata cho từng Tool để Spring AI tự động trích xuất JSON Schema đầy đủ (các field, required, type, description) cho LLM.
- Trong `ToolRegistry`:
  - Phương thức `getToolCallbacksForRoles(Set<String> roles, AgentContext context)` chuyển đổi các `AgentTool` được cấp quyền thành `ToolCallback` của Spring AI.
  - Khi `ToolCallback.call(...)` được Spring AI kích hoạt trong quá trình suy luận của LLM:
    - Ghi nhận `ai_tool_execution` (status `IN_PROGRESS` -> `SUCCEEDED` / `FAILED`, latency, params, result).
    - Bắn event SSE `tool.started` và `tool.completed` qua `SseBroadcaster`.
- Trong `AgentOrchestrator`:
  - Truyền danh sách `ToolCallback` vào `chatClient.prompt().tools(callbacks)`.
  - Spring AI tự động điều phối vòng lặp tool execution đa lượt (multi-turn) nếu LLM yêu cầu gọi tool.

---

### 2.2. Chat Memory & Context Management

#### Hiện trạng
- `ContextBuilder.buildUserPromptWithHistory` tự nối chuỗi `user: ...\nassistant: ...\n` thành một string duy nhất, làm mất cấu trúc `List<Message>` của OpenAI/Spring AI.
- `ContextBuilder.getRecentMessages` tự cắt sliding window.

#### Giải pháp Spring AI
- Tạo class `JpaChatMemory` triển khai `org.springframework.ai.chat.memory.ChatMemory`:
  - `add(String conversationId, List<Message> messages)`: Lưu các message mới vào `ai_message` thông qua `MessageRepository`.
  - `get(String conversationId, int lastN)`: Đọc N message gần nhất có role `user` hoặc `assistant` từ `MessageRepository`, map sang `org.springframework.ai.chat.messages.UserMessage` và `AssistantMessage`.
- Sử dụng `MessageChatMemoryAdvisor`:
  - Spring AI tự động truy xuất lịch sử, bổ sung vào prompt ngữ cảnh của model.
  - Sau khi model phản hồi, Spring AI tự động ghi `AssistantMessage` vào `ChatMemory`.

---

### 2.3. Structured Output (`AiExplanationService`)

#### Hiện trạng
- `cleanJsonString` trong `LlmClient` cắt bỏ ````json```` bằng chuỗi.
- `AiExplanationService` parse thủ công bằng `JsonParser.parseString(...)` và loop gson.

#### Giải pháp Spring AI
- Định nghĩa DTO phản hồi hoàn chỉnh `AiExplanationResultDto`:
  ```java
  public record AiExplanationResultDto(
      String overallSummary,
      List<StepAnalysis> stepAnalyses
  ) {}
  ```
- Thay thế đoạn gọi LLM trong `AiExplanationService` bằng:
  ```java
  AiExplanationResultDto result = chatClient.prompt()
      .system(promptBuilder.buildSystemPrompt())
      .user(promptBuilder.buildUserPrompt(assignment, resultDto))
      .call()
      .entity(AiExplanationResultDto.class);
  ```
- Spring AI tự sinh hướng dẫn format JSON schema, tự loại bỏ markdown fences và deserialize thẳng sang `AiExplanationResultDto`.

---

### 2.4. Real-time Token Streaming

#### Hiện trạng
- `AgentOrchestrator` gọi `llmClient.generateCompletion(...)` dạng blocking, sau đó gửi nguyên cục text qua `sseBroadcaster.sendDelta(runId, completion)`.

#### Giải pháp Spring AI
- Gọi streaming thông qua `ChatClient`:
  ```java
  chatClient.prompt()
      .system(systemPrompt)
      .advisors(chatMemoryAdvisor)
      .tools(tools)
      .stream()
      .content()
      .doOnNext(delta -> sseBroadcaster.sendDelta(runId, delta))
      .collectList()
      .map(chunks -> String.join("", chunks))
      .block();
  ```
- Token được đẩy thời gian thực (real-time) tới frontend SSE ngay khi model sinh ra.

---

## 3. Kế hoạch xác minh & Kiểm thử (Verification & Testing)
1. **Unit Tests**:
   - `JpaChatMemoryTest`: Kiểm tra lưu và lấy tin nhắn theo đúng thứ tự sequence và window size.
   - `ToolCallbackSchemaTest`: Kiểm tra JSON Schema sinh ra từ các tool không bị rỗng.
   - `AiExplanationServiceTest`: Kiểm tra structured output mapping thành công từ mock `ChatResponse`.
2. **Integration / System Tests**:
   - Chạy `mvn test` trên toàn bộ module `ai-assistant-service` đảm bảo 100% tests pass.
   - Kiểm tra tương thích với các DTO frontend và các SSE event listeners.

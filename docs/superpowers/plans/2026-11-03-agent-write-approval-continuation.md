# Agent Write Approval Continuation & Sequential Writes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khi người dùng đồng ý thực hiện một kế hoạch gồm nhiều thao tác ghi, agent tiếp tục vòng lặp suy luận để chuẩn bị và hỏi duyệt các bước kế tiếp thay vì thoát sớm sau bước đầu tiên; đồng thời giới hạn mỗi lượt chỉ gọi tối đa 1 thao tác ghi để loại bỏ hoàn toàn lỗi `PENDING_ACTION_EXISTS`.

**Architecture:** 
1. Thêm `priorMultiStepPlan(context)` trong `AgentRuntime` để phát hiện kế hoạch đa bước từ các tin nhắn đề xuất gần nhất.
2. Khi `confirmFromChat` thành công cho một bước thuộc kế hoạch đa bước: không `return` ngay mà nạp kết quả hoàn tất bước đó vào ngữ cảnh suy luận, duy trì danh sách tool và tiếp tục vòng lặp `for (int step = 0; step < maxModelSteps; step++)`. Đối với thao tác đơn lẻ, vẫn giữ luồng trả về câu tổng kết nhanh ban đầu.
3. Thêm runtime guard `AtomicBoolean writeProposedInTurn` trong mỗi model turn của `AgentRuntime` để chặn các lệnh ghi đồng thời trong cùng một lượt, trả về hướng dẫn an toàn `SEQUENTIAL_WRITE_REQUIRED`. Cập nhật system prompt trong `ContextBuilder` để LLM chủ động gọi tuần tự.

**Tech Stack:** Java 21, Spring Boot, Spring AI, JUnit 5, Mockito.

**Spec:** `docs/superpowers/specs/2026-11-03-agent-write-approval-continuation-design.md`

## Global Constraints

- Không bỏ qua bước duyệt ghi của người dùng (mọi thao tác ghi vẫn phải qua `WriteApprovalService` và hỏi xác nhận nếu cần).
- Giữ nguyên luồng xử lý nhanh (single-shot reply) cho các thao tác đơn lẻ không thuộc kế hoạch đa bước.
- Mỗi lượt suy luận (`modelStep`) chỉ được phép phát sinh tối đa 1 thao tác ghi `DOMAIN_WRITE`.
- Không ghi credentials, tokens hay payloads nhạy cảm vào trace logs.
- Toàn bộ 221 unit tests hiện có trong `ai-assistant-service` phải tiếp tục PASS.

---

### Task 1: Thêm quy tắc tuần tự hóa thao tác ghi vào System Prompt

**Files:**
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilder.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilderTest.java`

**Interfaces:**
- `ContextBuilder.SYSTEM_TEMPLATE`: Thêm quy tắc cấm gọi nhiều write tools trong cùng một phản hồi.

- [ ] **Step 1: Viết test kiểm tra quy tắc tuần tự hóa trong prompt**
  Trong `ContextBuilderTest.java`, thêm test `systemPromptContainsSequentialWriteRule`:
  ```java
  @Test
  void systemPromptContainsSequentialWriteRule() {
      ContextBuilder builder = new ContextBuilder();
      String prompt = builder.buildSystemPrompt("ROLE_LECTURER", null);
      assertTrue(prompt.contains("mỗi lượt chỉ gọi tối đa 1 công cụ ghi")
              || prompt.contains("mỗi lượt phản hồi chỉ được gọi tối đa 1"));
  }
  ```

- [ ] **Step 2: Chạy test để thấy fail**
  Chạy: `mvn -f ai-assistant-service/pom.xml -Dtest=ContextBuilderTest#systemPromptContainsSequentialWriteRule test`
  Expected: FAIL (chưa có quy tắc).

- [ ] **Step 3: Cập nhật `ContextBuilder.java`**
  Thêm vào mục QUY TẮC BẮT BUỘC số 4 trong `SYSTEM_TEMPLATE`:
  > *"4. Với thao tác ghi, BẮT BUỘC gọi tool ghi để lưu yêu cầu chờ đồng ý; chỉ hỏi người dùng đồng ý trong chat sau khi tool ghi trả về yêu cầu chờ xác nhận. Nếu chưa gọi tool ghi thì chưa có gì để xác nhận. Không được báo đã thực hiện trước khi có kết quả thực thi ở lượt chat sau. Khi có kế hoạch gồm nhiều thao tác ghi, BẮT BUỘC thực hiện tuần tự: mỗi lượt phản hồi chỉ được gọi tối đa 1 công cụ ghi; không gọi đồng thời nhiều công cụ ghi trong cùng một lượt."*

- [ ] **Step 4: Chạy lại test xác nhận PASS**
  Chạy: `mvn -f ai-assistant-service/pom.xml -Dtest=ContextBuilderTest test`
  Expected: PASS toàn bộ 8 tests của `ContextBuilderTest`.

- [ ] **Step 5: Commit task 1**
  `git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilder.java ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilderTest.java`
  `git commit -m "feat(ai): enforce sequential write rule in system prompt"`

---

### Task 2: Runtime Guard ngăn chặn nhiều Write Tools trong cùng một lượt

**Files:**
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentRuntime.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentRuntimeTest.java`

**Interfaces:**
- `AgentRuntime.boundedResult()`: Kiểm tra nếu tool là `DOMAIN_WRITE` và `writeProposedInStep` đã được kích hoạt, trả về lỗi hướng dẫn `SEQUENTIAL_WRITE_REQUIRED` thay vì cho phép gọi tiếp `propose()`.

- [ ] **Step 1: Viết test cho runtime guard**
  Trong `AgentRuntimeTest.java`, thêm test `guardsAgainstMultipleWritesInSingleModelTurn`:
  Giả lập model trả về 2 lệnh write tool trong cùng 1 turn. Lệnh 1 thành công (`AWAITING_CONFIRMATION`), lệnh 2 nhận được kết quả `SEQUENTIAL_WRITE_REQUIRED` mà không làm vỡ run và không gọi `propose()` lần 2.

- [ ] **Step 2: Chạy test để thấy fail**
  Chạy: `mvn -f ai-assistant-service/pom.xml -Dtest=AgentRuntimeTest#guardsAgainstMultipleWritesInSingleModelTurn test`
  Expected: FAIL.

- [ ] **Step 3: Triển khai Guard trong `AgentRuntime.java`**
  - Tại đầu mỗi `modelStep` (trong vòng lặp step):
    Tạo `AtomicBoolean writeProposedInStep = new AtomicBoolean(false);`
  - Trong `boundedResult()` hoặc wrapper callback của domain tools:
    Nếu `tool.getDefinition().effectType() == EffectType.DOMAIN_WRITE`:
    - Nếu `writeProposedInStep.get()` đã là `true`:
      Trả về ngay:
      `ToolResult.error("SEQUENTIAL_WRITE_REQUIRED", "Đã chuẩn bị một thao tác ghi trong lượt này. Vui lòng chờ người dùng xác nhận thao tác đó trước khi thực hiện thao tác ghi tiếp theo.", false)`
  - Trong `trackResult`:
    - Khi write tool thành công với `AWAITING_CONFIRMATION`, gọi `writeProposedInStep.set(true);`.

- [ ] **Step 4: Chạy lại test xác nhận PASS**
  Chạy: `mvn -f ai-assistant-service/pom.xml -Dtest=AgentRuntimeTest#guardsAgainstMultipleWritesInSingleModelTurn test`
  Expected: PASS.

- [ ] **Step 5: Commit task 2**
  `git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentRuntime.java ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentRuntimeTest.java`
  `git commit -m "fix(ai): guard against concurrent write tools in single model step"`

---

### Task 3: Continuation Loop sau khi duyệt thao tác thuộc Multi-Step Plan

**Files:**
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentRuntime.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentRuntimeTest.java`

**Interfaces:**
- `AgentRuntime.priorMultiStepPlan(AgentContext context)`: Quét các tin nhắn gần đây để tìm kế hoạch đa bước (chứa các bước được đánh số hoặc nhiều step items).
- `AgentRuntime.execute()`: Khi `decision == CONFIRM` và `approvalSucceeded`:
  - Nếu `priorMultiStepPlan` không rỗng: đưa thông tin hoàn tất bước hiện tại vào context, cập nhật `unpersistedProposal` với kế hoạch còn lại, và tiếp tục đi vào vòng lặp suy luận thay vì thoát ngay bằng `return completion;`.
  - Nếu `priorMultiStepPlan` rỗng (thao tác đơn): giữ nguyên luồng `return completion;` như hiện tại.

- [ ] **Step 1: Viết test cho Continuation Loop**
  Trong `AgentRuntimeTest.java`:
  - Thêm test `confirmPendingActionContinuesAgentLoopWhenMultiStepPlanExists`:
    Giả lập tin nhắn trước đó có đề xuất kế hoạch 4 bước ("1. Sửa bước 1... 2. Thêm bước 3..."). User gửi "ok". Action sửa bước 1 được confirm. Runtime không return mà tiếp tục gọi model step để chuẩn bị bước 3.
  - Đảm bảo test hiện có `confirmPendingActionFromChatExecutesAndGeneratesNaturalSummary` (thao tác đơn) vẫn PASS.

- [ ] **Step 2: Chạy test để thấy fail**
  Chạy: `mvn -f ai-assistant-service/pom.xml -Dtest=AgentRuntimeTest#confirmPendingActionContinuesAgentLoopWhenMultiStepPlanExists test`
  Expected: FAIL.

- [ ] **Step 3: Triển khai Continuation trong `AgentRuntime.java`**
  - Thêm hàm `priorMultiStepPlan(AgentContext context)`:
    Quét `messages.findScopedRecent(context.conversationId(), ...)` lấy các tin nhắn `assistant` gần nhất.
    Kiểm tra xem nội dung có chứa mẫu kế hoạch nhiều bước (ví dụ: `\n1.` và `\n2.`, hoặc `Bước 1` và `Bước 2`, hoặc `stepOrder` lặp lại) hay không.
  - Trong khối `if (decision == LlmClient.ApprovalDecision.CONFIRM)`:
    - Nếu `approvalSucceeded`:
      - Gọi `String multiStepPlan = priorMultiStepPlan(context);`
      - Nếu `multiStepPlan.isBlank()`:
        Giữ nguyên luồng: `onFinalDelta.accept(completion); return completion;`.
      - Nếu `!multiStepPlan.isBlank()`:
        - Đưa thông báo bước đã hoàn tất vào `unpersistedProposal`:
          `unpersistedProposal = "Thao tác '" + action.description() + "' đã được thực thi thành công (" + completion + ").\nKế hoạch tổng thể ban đầu:\n" + multiStepPlan + "\nHãy tiếp tục thực hiện bước tiếp theo trong kế hoạch (chỉ gọi 1 thao tác ghi mỗi lượt).";`
        - Cập nhật thông báo tiến trình cho giao diện/timeline.
        - **Không `return completion;`**, tiếp tục chạy xuống dưới để chọn tools và vào agent loop.

- [ ] **Step 4: Chạy test xác nhận PASS**
  Chạy: `mvn -f ai-assistant-service/pom.xml -Dtest=AgentRuntimeTest test`
  Expected: PASS toàn bộ các test của `AgentRuntimeTest`.

- [ ] **Step 5: Commit task 3**
  `git add ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentRuntime.java ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentRuntimeTest.java`
  `git commit -m "feat(ai): continue agent loop after confirming multi-step write action"`

---

### Task 4: Chạy toàn bộ Test Suite và Xác nhận Hoàn tất

**Files:**
- Toàn bộ test suite trong `ai-assistant-service`.

- [ ] **Step 1: Chạy toàn bộ 221+ tests**
  Chạy: `mvn -f ai-assistant-service/pom.xml test '-Dsurefire.argLine=-Xmx384m'`
  Expected: `BUILD SUCCESS`, 0 failures, 0 errors.

- [ ] **Step 2: Kiểm tra git status và diff sạch sẽ**
  Chạy: `git status` và `git diff --check`
  Expected: Không có cảnh báo cú pháp, không có file rác chưa track.

- [ ] **Step 3: Commit tổng kết nếu có cập nhật tài liệu progress**
  `git commit -m "chore: complete write approval continuation implementation"`

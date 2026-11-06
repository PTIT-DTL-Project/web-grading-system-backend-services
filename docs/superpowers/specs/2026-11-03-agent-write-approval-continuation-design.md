# Agent Write Approval Continuation & Sequential Execution Design

**Date:** 2026-11-03  
**Status:** Draft / Proposed  
**Author:** AI Assistant Team  

---

## 1. Context & Problem Statement

Khi người dùng yêu cầu thực hiện một kế hoạch gồm nhiều bước (ví dụ: sửa bước 1, thêm bước 3, thêm bước 4, sửa bước 2 cho một câu kiểm tra):
1. **Lỗi gọi đồng thời nhiều thao tác ghi (Concurrent Writes in Single Turn):**
   - Mô hình (LLM) trả về nhiều lệnh `updateTestStep` / `createTestStep` trong cùng một lượt (`modelStep`).
   - Lệnh thứ nhất được chuyển qua `WriteApprovalService.propose()`, tạo thành công `PendingAction` trong cơ sở dữ liệu với trạng thái `PENDING`.
   - Lệnh thứ hai chạy ngay sau đó trong cùng lượt, nhưng `WriteApprovalService.propose()` kiểm tra thấy đã có một `PendingAction` chưa duyệt trong cuộc trò chuyện, dẫn tới lỗi:
     `PENDING_ACTION_EXISTS: "Đang có thao tác chờ đồng ý trong chat; hãy trả lời thao tác đó trước"`.
   - Ngay sau lượt đó, biến `pendingQuestion` nhận được thông báo chờ duyệt của lệnh 1, khiến `AgentRuntime.execute()` lập tức `return` để gửi câu hỏi duyệt cho người dùng. Các lệnh còn lại (bước 3, bước 4) chưa bao giờ được gọi.

2. **Lỗi đứt đoạn sau khi duyệt (Missing Continuation Loop):**
   - Khi người dùng phản hồi xác nhận ("ok", "ok đi"), một lượt chạy mới (`run`) bắt đầu.
   - Đầu hàm `AgentRuntime.execute()`, hệ thống phát hiện có `PendingAction` đang chờ và xác nhận quyết định là `CONFIRM`.
   - Hệ thống thực thi thao tác đó qua `approvalService.confirmFromChat()`.
   - Ngay sau khi thực thi xong, hệ thống tạo câu thông báo hoàn tất và gọi **`return completion;`** lập tức kết thúc lượt chạy.
   - **Hậu quả:** Hệ thống không tiếp tục vòng lặp suy luận (`agent loop`), khiến các bước tiếp theo trong kế hoạch ban đầu (bước 2, bước 3, bước 4) bị bỏ rơi hoàn toàn.

---

## 2. Goals & Non-Goals

### Goals
- **Continuation sau khi xác nhận:** Khi người dùng đồng ý (`CONFIRM`), sau khi thực thi hành động đã duyệt, `AgentRuntime` không thoát ngay mà đưa kết quả thực thi vào ngữ cảnh hội thoại và tiếp tục vòng lặp suy luận để LLM tiếp tục các bước còn lại trong kế hoạch ban đầu.
- **Tuần tự hóa thao tác ghi (Single Write per Turn):** Đảm bảo mô hình chỉ chuẩn bị tối đa 1 thao tác ghi trong 1 lượt suy luận, tránh hoàn toàn lỗi `PENDING_ACTION_EXISTS`.
- **Dừng đúng lúc (Safe Termination):** Nếu bước tiếp theo là một thao tác ghi khác, agent dừng lại để hỏi duyệt bước đó; nếu đã hết việc, agent tổng kết toàn bộ kết quả cho người dùng.

### Non-Goals
- Không bỏ qua bước duyệt ghi của người dùng (mọi thao tác ghi vẫn phải qua `WriteApprovalService` nếu cấu hình yêu cầu).
- Không gom nhóm nhiều thao tác ghi phức tạp vào một batch transaction đơn lẻ ở mức backend service nếu backend chỉ hỗ trợ từng API riêng biệt.

---

## 3. Detailed Architecture & Design

### 3.1. Tiếp tục Agent Loop sau khi Confirm (`AgentRuntime.java`)

Hiện tại ở dòng 135–178 của `AgentRuntime.java`:
```java
if (decision == LlmClient.ApprovalDecision.CONFIRM) {
    ...
    outcome = approvalService.confirmFromChat(action.id(), context);
    ...
    onFinalDelta.accept(completion);
    return completion; // <-- Hiện tại thoát ngay ở đây
}
```

**Thay đổi thiết kế:**
- Nếu `approvalSucceeded`:
  - Khởi tạo `inFlight` message list (hoặc cập nhật context).
  - Thêm một UserMessage / System context thông báo kết quả:
    ```text
    Hệ thống đã thực hiện thành công thao tác ghi: [action.summary()].
    Kết quả: [outcome].
    Yêu cầu ban đầu của người dùng: [originalRequest].
    Hãy kiểm tra xem trong yêu cầu ban đầu còn công việc nào chưa hoàn tất không.
    - Nếu còn bước tiếp theo (ví dụ: thêm bước chấm, sửa bước khác), hãy thực hiện bước tiếp theo (chỉ gọi 1 thao tác ghi ở lượt này).
    - Nếu toàn bộ yêu cầu ban đầu đã hoàn thành, hãy tổng kết ngắn gọn cho người dùng.
    ```
  - **Không `return completion;`**. Thay vào đó, cho luồng tiếp tục đi xuống chuẩn bị tools và bước vào vòng lặp `for (int step = 0; step < properties.getLimits().getMaxModelSteps(); step++)`.
  - Nếu `latestUserText` là câu khẳng định ngắn ("ok", "ok đi"), gán `continuationRequest` để prompt hiểu ngữ cảnh công việc đang dở dang.

### 3.2. Giới hạn 1 Write Tool mỗi Model Turn

1. **System Prompt (`ContextBuilder.java` & `AgentRuntime.java`):**
   - Bổ sung quy tắc bắt buộc vào `SYSTEM_TEMPLATE`:
     > *"Khi cần thực hiện nhiều thao tác ghi (tạo/sửa/xóa), BẮT BUỘC thực hiện tuần tự: mỗi lượt phản hồi chỉ được gọi tối đa 1 công cụ ghi. Tuyệt đối không gọi 2 công cụ ghi trong cùng một lượt."*

2. **Runtime Guard tại `AgentRuntime.java`:**
   - Trong quá trình thiết lập callbacks cho lượt gọi model:
     - Duy trì một cờ `AtomicBoolean writeProposedInCurrentTurn = new AtomicBoolean(false);`.
     - Nếu một write tool đã được gọi và trả về `AWAITING_CONFIRMATION` (tạo pending action thành công):
       - Các write tool tiếp theo được gọi trong cùng lượt đó sẽ được trả về kết quả hướng dẫn an toàn:
         `ToolResult.error("SEQUENTIAL_WRITE_REQUIRED", "Đã chuẩn bị một thao tác ghi trong lượt này. Vui lòng chờ người dùng xác nhận thao tác đó trước khi thực hiện thao tác ghi tiếp theo.", false)`
       - Điều này ngăn chặn việc gọi `WriteApprovalService.propose()` lần 2 gây lỗi `PENDING_ACTION_EXISTS`.

### 3.3. Xử lý Single Action vs Multi-Step Action

- Đối với yêu cầu đơn lẻ (chỉ có 1 thao tác ghi, ví dụ "Tạo bài tập Toán"):
  - Sau khi confirm, agent loop chạy tiếp 1 lượt.
  - LLM nhận thấy yêu cầu đã hoàn tất, không gọi thêm tool nào nữa mà sinh câu tổng kết: *"Bài tập Toán đã được tạo thành công."*
  - Runtime trả câu tổng kết này cho người dùng và kết thúc.
- Đối với yêu cầu đa bước (sửa bước 1, thêm bước 3, 4, sửa bước 2):
  - Sau khi confirm bước 1, agent loop chạy tiếp.
  - LLM gọi tool cho bước 3.
  - Tool bước 3 tạo pending action mới -> `pendingQuestion` được gán -> runtime dừng và hỏi duyệt bước 3.
  - Người dùng "ok" -> lặp lại quy trình cho đến bước cuối cùng.

---

## 4. Error Handling & Edge Cases

1. **Thao tác confirm thất bại (`approvalSucceeded == false`):**
   - Nếu thao tác confirm bị lỗi (ví dụ API bên ngoài trả về lỗi 500 hoặc validation fail), runtime sẽ dừng lại và trả về thông báo lỗi chi tiết cho người dùng, không tiếp tục các bước sau vì dữ liệu phụ thuộc có thể không hợp lệ.
2. **Người dùng từ chối (`decision == REJECT`):**
   - Hủy pending action và `return completion;` như hiện tại (không tiếp tục các bước tiếp theo).
3. **Giới hạn số bước lặp (`maxModelSteps`):**
   - Cơ chế giới hạn số bước tối đa có sẵn trong `AgentRuntime` bảo vệ hệ thống không bị lặp vô tận.

---

## 5. Testing Plan

1. **Unit Test trong `AgentRuntimeTest.java`:**
   - `testConfirmActionContinuesAgentLoopWhenMoreStepsRemain`: Giả lập có 1 pending action, user gửi "ok". Xác nhận sau khi confirm thành công, runtime tiếp tục gọi model và thực hiện tool tiếp theo.
   - `testConfirmSingleActionReturnsCompletionWhenDone`: Giả lập 1 pending action đơn lẻ, user gửi "ok". Xác nhận sau khi confirm, model trả lời hoàn tất và kết thúc bình thường.
   - `testMultipleWritesInSingleTurnAreGuarded`: Giả lập model trả về 2 write tool calls trong cùng 1 turn. Xác nhận tool thứ 2 nhận được thông báo `SEQUENTIAL_WRITE_REQUIRED` thay vì làm vỡ flow.
2. **Toàn bộ regression test suite:**
   - Chạy toàn bộ 221 tests hiện có của `ai-assistant-service` để đảm bảo không phá vỡ bất kỳ tính năng nào đã hoàn thiện.


# Agent Step Intent Recovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Khi giảng viên yêu cầu thêm bước chấm vào câu kiểm tra đã có, agent giữ đúng đối tượng và dựng đề xuất `createTestStep`, kể cả khi summary cũ nói đang tạo câu kiểm tra.

**Architecture:** Định tuyến tool theo ý định mới nhất tại AgentRuntime, dùng lookup assignment → plan → step. Dùng summary như lịch sử, không như trạng thái công việc hiện tại; sửa hướng dẫn summarizer để xóa việc chờ đã hoàn tất. Phản hồi “cứ tạo theo mẫu” tiếp tục đề xuất bước chấm ngay trước đó với config đúng schema. Ghi pha lỗi/selected tools bằng metadata an toàn.

**Tech Stack:** Java 21, Spring Boot/Spring AI, JUnit 5, Mockito, Maven.

**Spec:** Hội thoại lỗi người dùng cung cấp, verified against ai_message/ai_conversation_summary DB 2026-11-01; proposal đã được người dùng chấp nhận.

## Global Constraints

- Không tạo plan mới nếu yêu cầu hiện tại là tạo step cho plan có sẵn.
- Không dùng ID từ summary cũ làm nguồn xác thực; đọc assignment/plan hiện tại theo tên và quyền giảng viên.
- Mẫu assistant được người dùng chấp nhận là giả định cho payload chờ duyệt, chưa phải lệnh ghi ngay.
- Tool config dùng `expected_status` và `assertions` với `kind`; không dùng `expectedStatusCode`/`expectedResponse`.
- Không ghi nội dung tin nhắn, credentials hoặc payload vào log chẩn đoán.

## Review Focus

- Câu “ok cứ tạo theo mẫu bạn gợi ý” chỉ gắn với bước assistant vừa gợi ý, không với plan đã tạo trước đó.
- Summary nói chờ name/sequenceOrder nhưng plan đã tạo; model không hỏi lại thông tin plan.
- Tool lookup có đủ `listVisibleAssignments`, `listTestPlans` và `createTestStep`; không chọn createTestPlan/createTestQuestion cho explicit step intent.
- Nếu LLM/provider lỗi, run báo lỗi riêng và log chỉ pha/mã/loại lỗi để chẩn đoán; không suy đoán là mạng.
- Trường hợp chưa có endpoint/spec hoặc mẫu chưa được người dùng chấp nhận vẫn yêu cầu thông tin còn thiếu.

---

### Task 1: Chọn đúng tool cho step intent

**Files:** Modify `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentRuntime.java`; test `.../orchestrator/AgentRuntimeTest.java`.

- [x] Viết regression test: latest user “tạo bước chấm cho Kiểm tra API thêm sách” với model selector trả createTestPlan; callback phải có lookup assignment/plan và createTestStep, không có createTestPlan/createTestQuestion. Test câu “ok cứ tạo theo mẫu bạn gợi ý” sau assistant đề xuất step có cùng routing.
- [x] Chạy `mvn -f ai-assistant-service/pom.xml -Dtest=AgentRuntimeTest test`, xác nhận RED đúng lý do.
- [x] Thêm nhận diện step intent theo latest user hoặc câu chấp nhận đề xuất step gần nhất; sau LLM selection, giữ lookup chain và createTestStep, bỏ write tool tạo plan trong trường hợp này. Prompt lượt model ghi rõ step của plan hiện có và mẫu được chấp nhận là giả định cần duyệt.
- [x] Chạy test riêng GREEN.

### Task 2: Summary và câu trả lời không được quay về việc cũ

**Files:** Modify `.../service/ConversationSummarizer.java`, `.../orchestrator/AgentRuntime.java`; test `.../service/ConversationSummarizerTest.java`, `.../orchestrator/AgentRuntimeTest.java`.

- [x] Viết test summary cũ “chờ name/sequenceOrder tạo câu” và tin nhắn gần đây “đã tạo plan; tạo step”: model context bỏ summary cũ cho step intent. Test summarizer prompt buộc cập nhật việc đã hoàn tất và không giữ ID chưa xác minh.
- [x] Chạy test riêng, xác nhận RED.
- [x] Cho step intent bỏ đoạn pending task cũ khỏi model prompt và ưu tiên latest user. Khi model vẫn hỏi name/sequenceOrder của plan thay vì step, dùng một lượt repair có giới hạn với nhắc đúng yêu cầu; không loop vô hạn.
- [x] Chạy test riêng GREEN.

### Task 3: Chẩn đoán lỗi provider và kiểm chứng

**Files:** Modify `.../orchestrator/AgentRuntime.java`; test `.../orchestrator/AgentRuntimeTest.java`.

- [x] Viết test lỗi chọn tool được ghi theo phase và fallback an toàn cho step intent khi selector lỗi. Lỗi stream model đã có log `modelFailed` và regression test cũ.
- [x] Ghi selected tool names và phase/error type an toàn; giữ UI error message không lộ chi tiết provider.
- [x] Chạy `mvn -f ai-assistant-service/pom.xml test` và `git diff --check`, xác nhận toàn suite GREEN.

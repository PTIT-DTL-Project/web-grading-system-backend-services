# Lecturer Assignment and Test Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cho agent giảng viên tạo bài GRADED/PRACTICE, câu kiểm tra và bước chấm theo hợp đồng backend hiện tại cùng các quy tắc đã kiểm chứng trong lecturer E2E plan.

**Architecture:** Cập nhật tool hiện có trong ai-assistant-service: DTO/validation tạo bài, schema và validation bước dùng chung cho tạo câu và tạo bước, capability tool, rồi context cho vai trò giảng viên. Course-service và executor-service sau rebase đã hỗ trợ 13 assertion và tính năng HTTP nâng cao; không đổi API của hai service đó.

**Tech Stack:** Java 21, Spring AI/Spring Boot, Jackson 2 trong ai-assistant-service, JUnit 5, Mockito, Maven.

**Spec:** `docs/superpowers/specs/2026-11-01-lecturer-assignment-test-tools-design.md`; tham chiếu nghiệp vụ `../web-grading-system-deploy/.opencode/plan/generate-data-api/lecturer-full-e2e-plan.md` (đường dẫn tính từ thư mục repo này).

## Global Constraints

- Chỉ sửa ai-assistant-service; giữ luồng duyệt thao tác ghi và thay đổi rebase hiện có.
- Một plan là một câu kiểm tra; không bắt mọi câu hoặc mọi bài dùng đủ loại step/assertion.
- Ưu tiên quy tắc đã chấm thật ở MD mục 17.17–17.26 và hợp đồng backend hiện tại trước ví dụ DB cũ trong cùng MD.
- Thiếu đặc tả endpoint, expected response, DB service/credentials của bài cụ thể thì agent phải hỏi, không lấy dữ liệu mẫu của MD để đoán.
- Không lưu MD dài vào system prompt; chỉ đưa quy tắc vận hành, còn tool schema/capability mô tả cấu trúc đầu vào.

## Review Focus

- PRACTICE không có classId phải đi qua tool và serialize JSON không chứa classId; GRADED thiếu classId bị chặn.
- GRADED/PRACTICE cùng title không được nhận lại kết quả idempotency sai purpose; title vẫn được backend kiểm tra duy nhất theo giảng viên.
- Config HTTP có 13 kind và tính năng `data[]`/`auto_inject_headers` hợp lệ không bị tool loại nhầm; config thiếu field hoặc method/path sai bị chặn.
- DB step dùng app port làm `connection.db_port` phải được hướng dẫn sửa theo compose (tool không biết app/DB port của bài); thiếu `PRIMARY_KEY.table_name` hoặc `COUNT(*)` với `row_count:0` phải bị chặn khi chắc chắn sai.
- Context chỉ áp dụng quy tắc soạn bài cho giảng viên và vẫn giữ quy tắc duyệt ghi; sinh viên không thấy hướng dẫn tạo test ẩn.

---

### Task 1: CreateAssignmentTool hỗ trợ GRADED và PRACTICE

**Files:**
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/client/dto/CreateAssignmentCommand.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/CreateAssignmentTool.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/CreateAssignmentToolTest.java`

**Interfaces:**
- Consumes: course-service `CreateAssignmentRequest` với `title`, `description`, `classId`, `purpose`, `topic`, `gradingStrategy`, cấu hình Docker.
- Produces: `CreateAssignmentCommand` thêm `String purpose, String topic`; `CreateAssignmentTool.getToolDefinition()` khai báo hai field; `execute(JsonNode, AgentContext)` validate purpose/classId/topic và chuyển đủ field.

- [ ] **Step 1: Viết test thất bại.** Test schema có `purpose` (`GRADED`, `PRACTICE`) và `topic` bắt buộc, `classId` không bắt buộc ở schema; GRADED có classId và topic gửi đúng DTO, PRACTICE có topic và không có classId tạo thành công. Test PRACTICE kèm classId, GRADED thiếu classId, thiếu topic đều trả `INVALID_ARGUMENTS` trước API. Test JSON serialization không có `classId` ở PRACTICE và idempotency không dùng chung kết quả giữa hai purpose. Cập nhật các test cũ đang tạo bài không có topic.
- [ ] **Step 2: Chạy test để thấy fail.** Run: `mvn -f ai-assistant-service/pom.xml -Dtest=CreateAssignmentToolTest test` từ root repo. Expected: FAIL ở schema/DTO/validation mới.
- [ ] **Step 3: Sửa code tối thiểu.** Cho `purpose` mặc định `GRADED`, `gradingStrategy` mặc định `STUDENT_DOCKER_COMPOSE`; validate `topic` không rỗng cho cả hai, ràng buộc `classId` theo purpose, giữ template bắt buộc cho lecturer compose. DTO serialize `null` bằng `@JsonInclude(NON_NULL)` hiện có. Giữ idempotency key cũ cho GRADED để không mất khả năng đối soát; thêm namespace PRACTICE và giá trị purpose vào key practice. Đừng tự điền topic hoặc Docker config từ MD.
- [ ] **Step 4: Chạy lại test.** Cùng lệnh ở Step 2. Expected: PASS.
- [ ] **Step 5: Commit riêng file của task.** `git add` đúng ba file trên rồi `git commit -m "feat: support graded and practice assignment creation"`.

### Task 2: Hợp đồng bước chấm cho tạo câu và tạo bước

**Files:**
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/DomainApiTool.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/config/DomainToolConfiguration.java`
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/GetTestStepCapabilitiesTool.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/DomainApiToolTest.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/lecturer/LecturerQueryToolsTest.java`

**Interfaces:**
- Consumes: `DomainApiTool.payloadSchema()` và `validateStepConfig(JsonNode step, String location)` hiện có; course-service `StepConfigValidator` là chuẩn cấu trúc.
- Produces: một định nghĩa cấu trúc step và validation dùng cho cả `createTestStep` lẫn từng `createTestQuestion.payload.steps[i]`; capability tool mô tả đúng 13 assertion và bốn step type.

- [ ] **Step 1: Viết test thất bại.** Kiểm tra schema tạo câu gồm `plan` và `steps`, schema tạo bước có đúng các field config HTTP/DB và 13 assertion kind; capability tool liệt kê đủ. Các test validate cùng một step qua cả hai tool: HTTP hợp lệ với `data` (1–10 object), `auto_inject_headers`, `extract`, `any_of` được nhận; method lạ, path không bắt đầu `/`, status ngoài 100–599, data quá 10, any_of rỗng, DB_SCHEMA_CHECK không có checks, PRIMARY_KEY thiếu table_name, DB_QUERY không có query, DB_MIGRATION statements rỗng bị chặn. Test `COUNT(*)` + `expected.row_count:0` bị chặn; không cấm `COUNT(*)` cho trường hợp khác.
- [ ] **Step 2: Chạy test để thấy fail.** Run: `mvn -f ai-assistant-service/pom.xml -Dtest=DomainApiToolTest,LecturerQueryToolsTest test`. Expected: FAIL ở schema/validation mới.
- [ ] **Step 3: Sửa code tối thiểu.** Mở rộng `stepConfigSchema()` thành object với các thuộc tính HTTP/DB và mô tả ràng buộc theo `stepType`; giữ `additionalProperties` mở cho field executor hỗ trợ, không dùng `oneOf` để tránh provider từ chối schema công cụ. Cập nhật `validateStepConfig` chỉ kiểm tra lỗi cấu trúc chắc chắn xác định được từ payload, cùng quy tắc cho create question và create step. Cập nhật mô tả trong `DomainToolConfiguration` để phân biệt assignment → plan/câu → step và thứ tự. Cập nhật `GetTestStepCapabilitiesTool` theo 13 kind, `data[]`, `auto_inject_headers`, DB connection/checks; tránh lặp giá trị mẫu như endpoint/DB name.
- [ ] **Step 4: Chạy lại test.** Cùng lệnh ở Step 2. Expected: PASS.
- [ ] **Step 5: Commit riêng file của task.** `git add` năm file trên rồi `git commit -m "feat: align lecturer test step tools with grading config"`.

### Task 3: Context agent giảng viên và kiểm chứng tích hợp

**Files:**
- Modify: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilder.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/ContextBuilderTest.java`

**Interfaces:**
- Consumes: schema/capability của Task 1–2.
- Produces: `buildSystemPrompt(String role, Task task)` thêm quy tắc cho lecturer, không thêm vào prompt student.

- [ ] **Step 1: Viết test thất bại.** Prompt lecturer nêu GRADED cần lớp, PRACTICE không gắn lớp, topic bắt buộc; một plan là một câu, thứ tự step/extract trước khi dùng biến; `query_params` thô; DB port nội bộ, `db_service`, `PRIMARY_KEY.table_name`, ý nghĩa result-set `row_count`; ưu tiên ví dụ đã sửa sau chấm thật; không bịa endpoint/DB credentials. Prompt student không chứa quy tắc tạo bài và vẫn chứa quy tắc bảo vệ test ẩn. Test quy tắc duyệt ghi cũ còn nguyên.
- [ ] **Step 2: Chạy test để thấy fail.** Run: `mvn -f ai-assistant-service/pom.xml -Dtest=ContextBuilderTest test`. Expected: FAIL ở quy tắc lecturer mới.
- [ ] **Step 3: Sửa `ContextBuilder`.** Thêm lecturer-only section khi roles chứa `ROLE_LECTURER`; giữ các quy tắc chung và tool catalog. Chỉ dẫn agent tra cứu assignment/plan/steps hiện có, chọn cấu hình theo đề bài và capability, hỏi khi thiếu dữ kiện không suy ra được; không nhúng toàn bộ 4.000+ dòng MD vào prompt.
- [ ] **Step 4: Chạy test riêng và suite.** Run: `mvn -f ai-assistant-service/pom.xml -Dtest=ContextBuilderTest test`, rồi `mvn -f ai-assistant-service/pom.xml test`. Expected: PASS. Nếu suite có lỗi nền, ghi rõ test và nguyên nhân, không báo toàn bộ pass.
- [ ] **Step 5: Commit riêng file của task.** `git add` đúng hai file trên rồi `git commit -m "feat: guide lecturer agent through test plan authoring"`.

## Self-review

- Task 1 phủ mục bài tập GRADED/PRACTICE và idempotency.
- Task 2 phủ câu/step, 13 assertion, tính năng HTTP nâng cao và lỗi DB đã kiểm chứng.
- Task 3 phủ hướng dẫn runtime, thứ tự tra cứu và không bịa dữ liệu; các test tách theo trách nhiệm.
- Không thay đổi API course/executor, không gọi môi trường chấm thật khi chỉ xác nhận hợp đồng cấu trúc.

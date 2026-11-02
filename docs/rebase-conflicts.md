# Báo cáo conflict khi rebase

Kiểm tra ngày 2026-11-02. Repo: `web-grading-system-backend-services`.

## Rebase lần 2 lên `86a1bdb` (origin/main sau PR #75)

- Đã phát lại 30 commit của `feat/improve-ai-agent-lookup`; rebase hoàn tất tại `60a3e6e` trước commit sửa test bổ sung.
- `ContextBuilder`: giữ hướng dẫn link theo vai trò từ remote (quy tắc 11–14), đồng thời giữ `{planSection}` và `{lecturerRules}` từ local. Quy tắc local cũ cho phép tự đề xuất smoke test `GET /` đã bị chính commit local mới hơn thay bằng quy tắc không tự đặt `GET /` hay status 200, nên không giữ hai chỉ dẫn mâu thuẫn.
- `GetTestStepCapabilitiesTool`: remote đã thay tool trả dữ liệu tĩnh bằng tool cùng tên gọi endpoint `/api/v1/internal/test-plans/capabilities` của course-service. Giữ phiên bản động để tránh đăng ký trùng tên và sai khác với validator; test chỉ dành cho tool tĩnh cũng không được khôi phục.
- `DomainApiTool`: giữ kiểm tra cấu hình local trước, sau đó gọi endpoint validate của course-service cho `createTestQuestion`, `createTestStep` và `updateTestStep`. Test đã được cập nhật để xác nhận bước validate trước API ghi.
- `LlmClient`: giữ cấu hình `AiAgentModelProperties` của remote cùng phần ghi trace của local; test trace dùng đúng loại cấu hình.
- Kiểm chứng `ai-assistant-service`: `mvn -q test` chạy 224 test, 0 failure/error/skipped. Các sửa test bổ sung sau rebase được commit riêng.

## Kết quả xử lý lần 1

- Đã rebase xong 29 commit lên `9820ac9` bằng `git rebase -X theirs 9820ac9`. Trong rebase, `theirs` là commit từ nhánh local `feat/improve-ai-agent-lookup`, nên các hunk xung đột ưu tiên code local. HEAD sau rebase: `b8eb3c8`.
- Git đã bỏ qua 3 commit có thay đổi tương đương đã nằm trên nhánh đích: `461663d`, `e78d4fb`, `4814fe1`.
- Sau rebase, đã xử lý thêm các phần tự ghép gây lỗi biên dịch ngoài marker: bỏ phương thức `looksLikeApprovalRequest` trùng từ nhánh đích; bỏ `topic` trùng trong `CreateAssignmentCommand` và tham số `topic` dư ở `CreateAssignmentTool`; bổ sung trường `scheduler` cho heartbeat của `SseBroadcaster`.
- Hai test bị ghép thêm kỳ vọng từ nhánh đích đã được trả về hành vi local: `DomainApiToolTest` không chờ một lệnh validate riêng và `CreateAssignmentToolTest` không kiểm tra giá trị `topic` không khớp request.
- Kiểm chứng: `mvn -q test` trong `ai-assistant-service` chạy 224 test, 0 failure, 0 error, 0 skipped; `git diff --check` không báo lỗi. Các sửa sau rebase và báo cáo được đưa vào một commit riêng.

## Trạng thái lúc phát hiện conflict

- Đang rebase nhánh `feat/improve-ai-agent-lookup` lên `9820ac9` (merge PR #72).
- Dừng ở lệnh `pick` đầu tiên, commit `82dd734` — `Select agent tools with LLM and persist write confirmations`. Sau lệnh này còn 28 lệnh rebase chưa chạy; danh sách dưới đây chỉ phản ánh conflict **hiện tại**, không dự đoán các commit tiếp theo.
- Có **5 file `UU` / 15 hunk conflict**. Tất cả đều là `both modified`. Trong marker, `HEAD` là nội dung từ nhánh đích tại `9820ac9`; phía `82dd734` là commit đang được phát lại. Khi rebase, không nên hiểu `HEAD` là phiên bản gốc của nhánh tính năng.

## Từng file conflict

| File | Hunk (dòng bắt đầu `<<<<<<<`) | Nguyên nhân / khác biệt cần quyết định |
| --- | --- | --- |
| `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentRuntime.java` | 82, 148, 394, 470, 499 | **82:** Nhánh đích giữ phản hồi riêng `planWriteReply` cho `createTestQuestion`/`createTestStep`, còn commit đang phát lại dùng `confirmationReply` và thử sinh phản hồi tự nhiên bằng LLM cho mọi thao tác thành công. **148:** Cả hai thêm `selector.selectFromModel`; nhánh đích còn đo và log thời gian chọn tool. **394/499:** Cùng sửa điều kiện tự tra cứu khi model hỏi ID: nhánh đích truyền `selected`, chặn tra cứu nếu đang hỏi xác nhận hoặc đã chọn tool ghi; commit mới chỉ kiểm tra registry có tool tra cứu phù hợp và đổi chữ ký hàm. Cần thống nhất cả chỗ gọi lẫn chữ ký. **470:** Nhánh đích mở rộng các câu xác nhận ngắn (`xác nhận`, `tiến hành`, `làm đi`, `chốt`); commit mới giữ tập câu ngắn hơn. |
| `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/SearchToolsTool.java` | 54 | Cùng sửa cơ chế tìm tool: nhánh đích ưu tiên `registry.searchAuthorized`, chỉ hỏi LLM khi registry không tìm thấy; commit mới hỏi LLM trước, dùng registry khi LLM trả `null`. Khác biệt này quyết định thứ tự ưu tiên và kết quả có thể trả về. |
| `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolSelector.java` | 26 | Nhánh đích thêm chọn tool theo điểm khớp từ khóa (tối đa 8 tool) và bỏ qua LLM nếu có kết quả; commit mới luôn hỏi LLM sau khi lọc theo quyền. Đây là xung đột hành vi chính của hai nhánh. |
| `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/orchestrator/AgentRuntimeTest.java` | 62, 110, 141, 160, 175, 223, 368 | **62/110/223:** Mock `ToolResultStore.offer` của nhánh đích luôn trả `"ok"`; commit mới trả lại chính dữ liệu đầu vào. **141/160/175:** Hai phía sửa cùng một test thành hai kịch bản khác nhau: registry tìm `listVisibleAssignments` trước khi hỏi lại, so với phục hồi khi provider báo `createTestStep` chưa có trong `request.tools`. Test setup, mock LLM, callback và kỳ vọng phải tách/ghép theo hành vi được chọn. **368:** Kỳ vọng sau xác nhận khác nhau: phản hồi tiến độ kế hoạch `Đã tạo **Q1**...` so với câu do LLM tạo `Đã tạo câu kiểm tra Q1...`; khớp với conflict ở `AgentRuntime.java:82`. |
| `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/tools/ToolSelectorTest.java` | 43 | Test nhánh đích đòi chọn tool theo điểm khớp mà không gọi LLM; test commit mới mock LLM rồi kiểm tra lọc tên trùng và tool không hợp lệ. Cùng một test đang kiểm chứng hai cơ chế chọn tool khác nhau. |

## Vấn đề liên quan cần kiểm tra khi giải conflict

- Trong `AgentRuntime.java`, `looksLikeApprovalRequest(String)` xuất hiện **hai lần liên tiếp** ở dòng 525 và 539, ngoài các marker conflict. Hai bản có điều kiện khác nhau (một bản yêu cầu từ xác nhận kèm động từ ghi, một bản yêu cầu dấu `?`). Đây là phần tự ghép gây trùng chữ ký phương thức; chỉ xóa 15 hunk marker vẫn sẽ để lại lỗi biên dịch nếu không hợp nhất hai bản này.
- `LlmClientTest.java` đang được stage như thay đổi bình thường, **không phải conflict**. Các file `.env*`, log, SQL/JSON/CSV và `docs/db_report.md` hiện là untracked; không nằm trong danh sách conflict. Tránh `git add .` khi đánh dấu đã xử lý conflict nếu không muốn đưa chúng vào commit.
- Tại thời điểm lập danh sách ban đầu, chưa sửa file Java và chưa chạy test vì mã còn marker conflict. Trạng thái hiện tại xem mục "Kết quả xử lý" ở đầu tài liệu.

## Lệnh đối chiếu

`git status`, `git ls-files -u`, `git show --stat REBASE_HEAD`, `rg -n '^(<<<<<<<|=======|>>>>>>>)'` trên 5 file và `git diff --check`.

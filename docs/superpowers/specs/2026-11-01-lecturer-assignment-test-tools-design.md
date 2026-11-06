# Thiết kế: tool tạo bài tập và câu kiểm tra theo lecturer E2E plan

## Mục đích và phạm vi

Agent giảng viên tạo được bài GRADED/PRACTICE, câu kiểm tra (plan), và bước chấm đúng hợp đồng API và các bài học đã được xác nhận trong `web-grading-system-deploy/.opencode/plan/generate-data-api/lecturer-full-e2e-plan.md`. Chỉ thay đổi ai-assistant-service; không sửa API course-service/executor-service hoặc dữ liệu bài tập đang có.

## Cách tiếp cận

- **Đã chọn:** cập nhật schema, validation và mô tả của các tool hiện có, kèm hướng dẫn ngắn trong system context. Ít ảnh hưởng code đang thay đổi và chặn lỗi trước khi gọi API.
- Phương án khác: tạo bộ schema dùng chung cho mọi tool. Đồng bộ hơn nhưng cần refactor rộng và không cần thiết cho phạm vi này.

## Hành vi cần có

- `createAssignment` nhận `purpose` và `topic`. `topic` bắt buộc cho cả hai purpose; GRADED cần `classId`, PRACTICE không gửi `classId`. Giữ mặc định GRADED và `STUDENT_DOCKER_COMPOSE` khi người dùng không nói rõ. Truyền đủ các trường đến course-service; idempotency phân biệt purpose và trường hợp không có lớp. Không tự tạo tên/trường còn thiếu từ ví dụ của plan.
- `createTestPlan` và `createTestQuestion` hướng dẫn rõ một plan là một câu kiểm tra; `sequenceOrder` là thứ tự câu, `weight` là trọng số. `createTestQuestion` nhận `plan` và mảng `steps` có thứ tự.
- `createTestStep` và các step trong `createTestQuestion` dùng cùng hướng dẫn và validation theo `stepType`: HTTP_REQUEST dùng `method`, `path`, `expected_status`, assertions, extract, query_params, data[] và auto_inject_headers khi phù hợp; DB_QUERY dùng `query`, `connection`, `expected`; DB_SCHEMA_CHECK dùng `checks`, `connection`; DB_MIGRATION dùng `statements`, `connection`. Không bắt mọi câu kiểm tra chứa mọi loại step/assertion; chọn theo yêu cầu bài.
- Sau rebase, course-service hỗ trợ đủ 13 assertion trong MD: `status`, `json_path`, `contains`, `body_structure`, `body_equals`, `field_equals`, `array_length`, `contains_item`, `json_path_equals`, `greater_than`, `less_than`, `matches_regex`, `any_of`. Tool phải đưa đúng tên và trường bắt buộc của từng kind cho agent; validation chung cho cả createTestStep/createTestQuestion. HTTP method/path/status, extract, data[] tối đa 10 phần tử, auto_inject_headers và DB config phải đối chiếu với validator/backend hiện tại. `getTestStepCapabilities` cũng phải mô tả đủ khả năng này.
- Với DB chạy trong Docker Compose: `connection.db_service` là tên service DB, `db_port` là port nội bộ của DB, không phải port HTTP của app; các thông tin DB phải khớp compose. `PRIMARY_KEY` có `table_name`. `row_count` đếm số hàng của result set; để kiểm tra không còn bản ghi, dùng query lọc ID thay vì `SELECT COUNT(*)` với `row_count: 0`. Biến `${...}` phải được extract ở bước trước; `query_params` dùng giá trị thô.
- Context agent chỉ áp dụng các quy tắc này cho giảng viên, yêu cầu tra cứu assignment/plan/step hiện có trước khi thêm bước, và không coi các giá trị mẫu hay bản ghi chạy thử trong MD là dữ liệu mặc định của bài mới. MD chứa ví dụ DB cũ mâu thuẫn với các mục sửa sau chấm thật; ưu tiên quy tắc đã kiểm chứng ở mục 17.17–17.26 và hợp đồng backend hiện tại. Nếu thiếu endpoint, expected response, Docker Compose service/DB hoặc credentials của bài cụ thể, agent phải nêu thiếu thông tin thay vì bịa để tạo step.

## Giới hạn và kiểm chứng

Giữ luồng duyệt thao tác ghi hiện có. Tránh đụng thay đổi dở ngoài các vùng liên quan. Test schema/validation và serialization của GRADED/PRACTICE, ma trận 13 assertion và các config HTTP/DB tiêu biểu, cùng nội dung context giảng viên. Chạy test của ai-assistant-service. Validation cấu trúc không thể chứng minh endpoint hay DB của một bài chưa được mô tả là đúng; cần đặc tả bài hoặc compose thực tế cho phần đó.

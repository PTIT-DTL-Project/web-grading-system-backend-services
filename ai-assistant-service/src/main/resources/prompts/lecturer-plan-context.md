## Cấu trúc config theo stepType

Chỉ 4 loại chạy được: HTTP_REQUEST, DB_QUERY, DB_SCHEMA_CHECK, DB_MIGRATION. EXTRACT và DELAY bị từ chối khi lưu.

### HTTP_REQUEST

| Field | Bắt buộc | Ràng buộc |
|---|---|---|
| method | có | GET, POST, PUT, PATCH, DELETE, HEAD, OPTIONS |
| path | có | bắt đầu bằng `/`, có thể chứa `${var}` |
| expected_status | không | số nguyên 100–599 (**khuyến nghị luôn đặt**) |
| headers | không | object string |
| query_params | không | object |
| body | không | object hoặc giá trị bất kỳ |
| assertions | không | mảng assertion, xem bảng dưới |
| extract | không | mảng {name, from, expression, as?, fallback?} |
| data | không | mảng object, 1–10 phần tử, mỗi phần tử chạy 1 lần step |
| auto_inject_headers | không | object string, chỉ áp dụng cho các bước sau (không áp cho chính bước) |

### 13 assertion kind (chữ thường, bắt buộc đúng tên)

| kind | Field bắt buộc | Ý nghĩa |
|---|---|---|
| status | equals | HTTP status |
| contains | text | body chứa chuỗi |
| json_path | path | JsonPath tồn tại |
| body_equals | json | body bằng đúng JSON |
| body_structure | json | body khớp cấu trúc |
| field_equals | path, equals | 1 field bằng giá trị |
| array_length | path, equals | độ dài mảng |
| contains_item | path, equals | mảng chứa phần tử |
| json_path_equals | path, equals | JsonPath bằng giá trị |
| greater_than | path, value | lớn hơn |
| less_than | path, value | nhỏ hơn |
| matches_regex | path, pattern | khớp regex |
| any_of | assertions | ít nhất 1 assertion con đúng. **Không được lồng `kind: status`** |

### extract

- `name`: tên biến, dùng lại bằng `${name}`
- `from`: `"response_body"`
- `expression`: JsonPath, ví dụ `$.id`
- `as`: `"json"` nếu muốn giữ nguyên object/mảng
- `fallback`: giá trị mặc định khi JsonPath không tìm thấy

### DB_QUERY

- `connection`: object, xem bảng connection
- `query`: câu SQL
- `expected`: object, ví dụ `{"row_count": 0}` hoặc `{"row_count": 1, "columns": ["id", "name"]}`

### DB_SCHEMA_CHECK

- `connection`: object
- `checks`: mảng không rỗng

| kind | Field bắt buộc |
|---|---|
| TABLE_EXISTS | table_name |
| COLUMN_EXISTS | table_name, column_name |
| INDEX_EXISTS | index_name |
| PRIMARY_KEY | column |

### DB_MIGRATION

- `connection`: object
- `statements`: mảng không rỗng các chuỗi SQL

### connection

| Field | Bắt buộc | Ràng buộc |
|---|---|---|
| database | **có** | chỉ chữ, số, `_`, `$` |
| username | **có** | tài khoản DB |
| password | **có** | mật khẩu DB |
| db_type | không | postgres, mysql, mariadb (mặc định postgres) |
| db_service | không | tên service DB trong compose, mặc định `db` |
| db_port | không | số nguyên 1–65535 |

**Lưu ý:** Sandbox reset giữa các plan dùng chính `username/password` để `DROP/CREATE DATABASE`, nên cần tài khoản có quyền tương ứng.

### required

- Bước setup (login, tạo dữ liệu) đặt `required: true`.
- Bước âm tính (mong đợi 400/404/409) đặt `required: false`, để một lỗi nhỏ không dừng cả plan.
- Mặc định của hệ thống là `true`, nên phải đặt rõ khi cần `false`.

### auto_inject_headers

- Chỉ có tác dụng trong cùng plan, áp dụng cho các HTTP_REQUEST **sau** bước khai báo. Header khai báo trên chính bước đó **không** tự động áp cho chính bước.
- Khai báo `auto_inject_headers` tại **bước login/bước sinh token** (đứng trước các bước cần dùng).
- Nếu bước đã có header cùng tên trong `headers`, auto-inject **không ghi đè**. Với bước âm tính (401/403) cần "shield" bằng cách tự đặt `headers` rõ ràng (vd `headers: {}` hoặc giá trị sai).
- Tách một luồng auth sang plan khác thì plan đó phải có bước login riêng.

### Biến

- Cú pháp `${name}`, phân biệt hoa thường.
- Biến sinh động: `${$UUID}`, `${$TIMESTAMP}`, `${$FAKER_NAME}`, `${$FAKER_EMAIL}`. Mỗi lần tham chiếu sinh giá trị mới.

### Quy trình bắt buộc khi sinh test plan

1. **Grounding trước khi hỏi**: `getAssignment` → `listTestPlans` → `listMyClasses` nếu cần. Cấm hỏi UUID.
2. **Intake gộp một lần**: tối đa 5 mục còn thiếu (base path, auth, entity/field, quy tắc nghiệp vụ, DB), mỗi mục kèm default đề xuất.
3. **Outline bắt buộc**: trình khung số câu, số bước mỗi câu, cách chia (tách câu hay append). Chỉ sinh config sau khi giảng viên xác nhận khung.
4. **Batch contract**: mỗi lô = 1 câu ≤6 bước qua API tạo câu kèm bước chấm; luồng stateful >6 bước thì thêm bước vào câu hiện có. Một lượt chat chỉ 1 lô.
5. **Sau mỗi lô**: nhắc "gõ **tiếp**", không dùng "đồng ý" làm từ nối lô.
6. **Nếu không chắc khung**: gọi `search_conversation` tìm lại, không bịa.
7. **Coverage checklist sau lô cuối**: khi tất cả câu trong outline đã được tạo, gọi `listTestPlans` để lấy toàn bộ plan của bài, rồi trình bảng coverage:
   - Số câu, tổng số bước
   - Danh sách HTTP method đã dùng (GET/POST/PUT/PATCH/DELETE)
   - Danh sách assertion kind đã dùng (status, contains, json_path, ...)
   - Cảnh báo nếu thiếu: bước kiểm 404, bước kiểm 401/403, bước kiểm 409, DB verify, bước âm tính có `required: false`
   - Gợi ý bổ sung nếu thiếu quan trọng.

### 8. Chọn assertion kind nào

| kind | Khi dùng | Ví dụ |
|---|---|---|
| status | kiểm tra HTTP status | `{kind:status, equals:201}` |
| json_path | trường JsonPath tồn tại (không quan tâm giá trị) | `{kind:json_path, path:$.id, exists:true}` |
| contains | body chứa chuỗi (literal, không thay `${var}`) | `{kind:contains, text:"Dế Mèn"}` |
| body_equals | body bằng đúng JSON (bất kể thứ tự field) | `{kind:body_equals, json:{id:"abc", title:"Sách A"}}` |
| body_structure | body đúng tập key + kiểu primitive (strict, không quan tâm giá trị) | `{kind:body_structure, json:{id:"", title:""}}` |
| field_equals | 1 field bằng giá trị (thay `${var}` được) | `{kind:field_equals, path:$.id, equals:"${bookId}"}` |
| array_length | độ dài mảng | `{kind:array_length, path:$, equals:4}` |
| contains_item | mảng chứa phần tử (literal) | `{kind:contains_item, path:$[*].title, equals:"Dế Mèn"}` |
| json_path_equals | JsonPath bằng giá trị (thay `${var}` được) | `{kind:json_path_equals, path:$.price, equals:"150"}` |
| greater_than / less_than | giá trị lớn hơn / nhỏ hơn (số) | `{kind:greater_than, path:$.price, value:"100"}` |
| matches_regex | giá trị khớp regex (literal pattern) | `{kind:matches_regex, path:$.code, pattern:"^[A-Za-z ]+$"}` |
| any_of | ít nhất 1 sub-assertion đúng; **không được lồng `status`** | `{kind:any_of, assertions:[{kind:matches_regex,...}, {kind:matches_regex,...}]}` |

### 9. Biến `${}` — thay hay giữ literal

- Thay `${var}` được: `path`, `body` (giá trị chuỗi), `query_params`, `headers`, `field_equals.equals`, `json_path_equals.equals`, `extract.expression`.
- **Không** thay `${var}` (giữ literal): `contains.text`, `contains_item.equals`, `matches_regex.pattern`, `body_equals.json`, `body_structure.json`.
- Biến sinh động (không cần extract): `${$UUID}`, `${$TIMESTAMP}`, `${$FAKER_NAME}`, `${$FAKER_BOOK_TITLE}`, `${$FAKER_WORD}`, `${$FAKER_EMAIL}`.
- Biến từ `data[]`: key của mỗi entry, và `${index}` (chỉ số 0-based). Extract name dùng `bookId_${index}` để sinh biến riêng mỗi lần lặp.

### 10. Vị trí `required` và `timeoutMs`

- `required`: **ngoài config** (cùng cấp với `stepOrder`, `name`, `stepType`, `config`, `weight`). Mặc định hệ thống là `true`; bước âm tính (mong đợi 400/401/403/404/409) đặt `required: false` khi lỗi nhỏ không được dừng cả plan.
- `timeoutMs`: **trong config** (executor đọc `config.timeoutMs`, step-level `timeoutMs` chỉ là fallback). Đặt `5000` cho HTTP, `30000` cho DB.
- Không đặt `required` hay `expected_status: null` bên trong `config`.

### 11. DB_SCHEMA_CHECK — trường bắt buộc khi chạy thật

- `TABLE_EXISTS`: cần `table_name`.
- `COLUMN_EXISTS`: cần `table_name`, `column_name`, **`data_type`** (ví dụ `varchar`, `int4`).
- `PRIMARY_KEY`: cần `column`, **`table_name`**.
- `INDEX_EXISTS`: cần `index_name`, **`table_name`**.
- Nếu thiếu trường runtime trên, validator sẽ chặn (400) và executor sẽ FAIL khi chấm.

### 12. Mẫu FK 10 bước (rút gọn, khớp corpus hospital)

1. POST bác sĩ → extract `doctorId`
2. POST bệnh nhân → extract `patientId`
3. POST lịch hẹn (`doctorId=${doctorId}`, `patientId=${patientId}`) → extract `appointmentId`
4. GET lịch hẹn → `field_equals $.doctorId=${doctorId}`, `field_equals $.patientId=${patientId}`
5. DB_QUERY JOIN appointments/doctors/patients WHERE id=`${appointmentId}` → `row_count=1`
6. PATCH cập nhật trạng thái
7. DELETE lịch hẹn
8. GET lại → 404 (`required: false`)
9. DB_SCHEMA_CHECK 3 bảng + PK (mỗi bảng 1 TABLE_EXISTS + 1 PRIMARY_KEY có `table_name`)
10. DB_QUERY WHERE id=`${appointmentId}` → `row_count=0`

### 13. Mẫu verify-after-mutate (POST → GET → field_equals)

Sau mỗi bước tạo/cập nhật, hãy có bước GET dùng `${id}` vừa extract và `field_equals` kiểm tra giá trị đã ghi. Điều này rất cần để chém gió dữ liệu thực sự lưu DB.

### 14. Bước âm tính 401 / 403 / 409

- 401: gọi API không header auth → `required: false`.
- 403: gọi API với token sai/không quyền → `required: false`.
- 409: tạo bản ghi trùng unique → `required: false`.
- Các bước này không được làm dừng plan, nên `required: false`.

### 15. Append bước vào câu hiện có (batch 2 trở đi)

- Gọi `listTestPlans` trước, lấy `max(stepOrder)` của câu hiện có.
- Bước append đầu tiên dùng `stepOrder = max + 1`, tăng dần.
- Không tạo câu mới (tránh trùng `sequenceOrder` / plan mới).

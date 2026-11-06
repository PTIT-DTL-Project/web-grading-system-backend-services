# 📊 Báo cáo Cấu trúc & Dữ liệu Database

> **Hệ thống**: Web Grading System Backend (PTIT)  
> **Host**: Neon Serverless PostgreSQL  
> **Thời điểm khám phá**: 30/09/2026

---

## 🏗️ Kiến trúc tổng thể

```
┌─────────────────────────────────────────────────────────────────────┐
│                  NEON POSTGRESQL (1 server)                         │
│                                                                     │
│  assignment_db         submission_db      result_db   executor_db   │
│  (course-service)  (submission-service) (result-svc) (executor-svc) │
└─────────────────────────────────────────────────────────────────────┘

Luồng chấm thi:

  [SV nộp bài]
       │ POST /submissions/presigned-url
       ▼
  submission_db.submissions (status=PENDING)
       │ Webhook S3 → Kafka: submission-uploaded
       ▼
  executor_db.grading_jobs ──► grading_sagas
       │                            │
       │   DOWNLOAD_ARTIFACT ◄──────┤
       │   BOOT_COMPOSE      ◄──────┤  (grading_saga_steps)
       │   RUN_TEST_STEPS    ◄──────┤
       │   SCORE_REPORT      ◄──────┘
       │
       ├──► grading_step_results (raw HTTP req/resp)
       │
       │ REST API call
       ▼
  result_db.results + step_results
       │
       │ REST API call
       ▼
  submission_db.submissions (status=DONE)
```

### Quan hệ cross-service (UUID references, không có DB FK cứng)

| Từ | Cột | Đến |
|----|-----|-----|
| `submission_db.submissions` | `assignment_id` | `assignment_db.assignments.id` |
| `submission_db.submissions` | `student_id` | Keycloak `user_entity.id` |
| `submission_db.submissions` | `plan_id` | `assignment_db.test_plans.id` |
| `executor_db.grading_jobs` | `submission_id` | `submission_db.submissions.id` |
| `executor_db.grading_jobs` | `assignment_id` | `assignment_db.assignments.id` |
| `executor_db.grading_jobs` | `plan_id` | `assignment_db.test_plans.id` |
| `result_db.results` | `submission_id` | `submission_db.submissions.id` |
| `result_db.results` | `assignment_id` | `assignment_db.assignments.id` |
| `result_db.results` | `student_id` | Keycloak `user_entity.id` |
| `assignment_db.assignments` | `class_id` | `assignment_db.classes.id` |
| `assignment_db.class_students` | `student_user_id` | Keycloak `user_entity.id` (nullable) |

---

## 🗄️ `assignment_db` — course-service

> Quản lý lớp học, bài tập, sinh viên và kế hoạch chấm thi. 4 Flyway migrations.

### Tổng quan bảng

| Bảng | Số bản ghi |
|------|-----------|
| `assignment_docker_images` | ⚪ 0 |
| `assignments` | 🟢 14 |
| `class_students` | 🟢 6 |
| `classes` | 🟢 13 |
| `docker_images` | ⚪ 0 |
| `score_components` | 🟢 8 |
| `student_scores` | 🟢 5 |
| `test_plans` | 🟢 8 |
| `test_steps` | 🟢 36 |

---

### `assignment_docker_images` (0 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `assignment_id` | uuid | 🔗 FK · NOT NULL |
| `docker_image_id` | uuid | 🔗 FK · NOT NULL |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |

> *(Bảng rỗng — chưa có dữ liệu)*

---

### `assignments` (14 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `class_id` | uuid | 🔗 FK · NOT NULL |
| `owner_id` | uuid | NOT NULL |
| `title` | character varying(255) | NOT NULL |
| `description` | text |  |
| `grading_strategy` | character varying(30) | NOT NULL · default: STUDENT_DOCKER_COMPOSE |
| `docker_compose_template` | text |  |
| `docker_compose_port` | integer | NOT NULL · default: 8080 |
| `startup_timeout_ms` | integer | NOT NULL · default: 60000 |
| `execution_timeout_ms` | integer | NOT NULL · default: 300000 |
| `max_memory_mb` | integer | NOT NULL · default: 256 |
| `max_cpu` | real | NOT NULL · default: 0.5 |
| `published` | boolean | NOT NULL · default: false |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |

**Dữ liệu mẫu (5 bản ghi):**

| # | `id` | `class_id` | `owner_id` | `title` | `description` | `grading_strategy` | `docker_compose_template` | `docker_compose_port` | `startup_timeout_ms` | `execution_timeout_ms` | `max_memory_mb` | `max_cpu` | `published` | `created_at` | `updated_at` | `deleted_at` |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | c9952022-4906-4a56-961c-13c49afb903d | 6c5f1121-cf1a-4cbf-acaa-1c960405e15a | 2d93941a-4221-458b-a03d-43bd6315d02e | Ex-1787554251-v2 | updated | STUDENT_DOCKER_COMPOSE | `null` | 8080 | 60000 | 300000 | 512 | 0.5 | true | 2026-08-24T06:50:54.614Z | 2026-08-24T06:51:00.202Z | `null` |
| 2 | 564b02ed-2985-495a-9b85-adfde4dd4ee6 | e66feb9d-c8de-40f3-9690-0bdafdcd16bf | 2d93941a-4221-458b-a03d-43bd6315d02e | Ex-1787554624-v2 | updated | STUDENT_DOCKER_COMPOSE | `null` | 8080 | 60000 | 300000 | 512 | 0.5 | true | 2026-08-24T06:57:07.230Z | 2026-08-24T06:57:13.038Z | `null` |
| 3 | 4a28fb5e-3449-4885-94c3-9f2bca9ce838 | f47b5fb6-21c3-4a51-8ac2-e4382a7dc2f3 | 2d93941a-4221-458b-a03d-43bd6315d02e | Other-1787555303 | `null` | STUDENT_DOCKER_COMPOSE | `null` | 8080 | 60000 | 300000 | 256 | 0.5 | false | 2026-08-24T07:08:34.028Z | 2026-08-24T07:08:35.839Z | 2026-08-24T07:08:35.837Z |
| 4 | 2fc924af-9f2c-432b-9f01-a37073a74c0b | f47b5fb6-21c3-4a51-8ac2-e4382a7dc2f3 | 2d93941a-4221-458b-a03d-43bd6315d02e | Ex-1787555303-v2 | updated | STUDENT_DOCKER_COMPOSE | `null` | 8080 | 60000 | 300000 | 512 | 0.5 | true | 2026-08-24T07:08:26.087Z | 2026-08-24T07:08:37.094Z | `null` |
| 5 | ff860f4e-b77e-480c-b52f-10680f3c912a | 96b64a75-0258-4d01-a892-ef28cc5bef88 | 2d93941a-4221-458b-a03d-43bd6315d02e | exs-assign-1787562606 | `null` | STUDENT_DOCKER_COMPOSE | `null` | 8080 | 60000 | 300000 | 256 | 0.5 | true | 2026-08-24T09:10:08.425Z | 2026-08-24T09:10:09.367Z | `null` |

---

### `class_students` (6 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `class_id` | uuid | 🔗 FK · NOT NULL |
| `student_code` | character varying(50) | NOT NULL |
| `student_name` | character varying(200) | NOT NULL |
| `email` | character varying(200) |  |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |
| `student_user_id` | uuid |  |

**Dữ liệu mẫu (5 bản ghi):**

| # | `id` | `class_id` | `student_code` | `student_name` | `email` | `created_at` | `updated_at` | `deleted_at` | `student_user_id` |
|---|---|---|---|---|---|---|---|---|---|
| 1 | a2d19e2b-27b0-4d7f-b99b-2a1224da77f0 | 19825639-710a-49c6-9072-789f0205aa86 | B22DCCN001 | Nguyen Van An | b22dccn001@ptit.edu.vn | 2026-08-23T08:41:34.526Z | 2026-08-23T08:41:34.526Z | `null` | `null` |
| 2 | f43c06c6-4ad2-42a1-b664-4fe0d1a39e0f | 19825639-710a-49c6-9072-789f0205aa86 | B22DCCN002 | Tran Thi Bich | b22dccn002@ptit.edu.vn | 2026-08-23T08:41:35.096Z | 2026-08-23T08:41:35.096Z | `null` | `null` |
| 3 | b4027f4d-8fb5-4bd1-85c6-04ca5915c63f | 19825639-710a-49c6-9072-789f0205aa86 | B22DCCN003 | Le Van Cuong |  | 2026-08-23T08:41:35.576Z | 2026-08-23T08:41:35.576Z | `null` | `null` |
| 4 | da2e552d-89bd-4d7e-8964-7fb7a96d33fd | 19825639-710a-49c6-9072-789f0205aa86 | B22DCCN004 | Pham Duy Dung | b22dccn004@ptit.edu.vn | 2026-08-23T08:41:36.067Z | 2026-08-23T08:41:36.068Z | `null` | `null` |
| 5 | 99ab7f28-ca71-4ba0-abdf-1b80439fab7c | 19825639-710a-49c6-9072-789f0205aa86 | B22DCCN005 | Vo Thi Em | b22dccn005@ptit.edu.vn | 2026-08-23T08:41:36.555Z | 2026-08-23T08:41:36.555Z | `null` | `null` |

---

### `classes` (13 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `owner_id` | uuid | NOT NULL |
| `name` | character varying(255) | NOT NULL |
| `semester` | character varying(20) | NOT NULL |
| `status` | character varying(20) | NOT NULL · default: ACTIVE |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |

**Dữ liệu mẫu (5 bản ghi):**

| # | `id` | `owner_id` | `name` | `semester` | `status` | `created_at` | `updated_at` | `deleted_at` |
|---|---|---|---|---|---|---|---|---|
| 1 | 19825639-710a-49c6-9072-789f0205aa86 | 2d93941a-4221-458b-a03d-43bd6315d02e | test | 20261 | ARCHIVED | 2026-08-23T08:22:44.148Z | 2026-08-23T08:23:44.655Z | `null` |
| 2 | 6c5f1121-cf1a-4cbf-acaa-1c960405e15a | 2d93941a-4221-458b-a03d-43bd6315d02e | ex-class-A-1787554251 | 20261 | ACTIVE | 2026-08-24T06:50:52.436Z | 2026-08-24T06:50:52.436Z | `null` |
| 3 | a3cb1e9d-24ab-40d6-b496-f75b41a723d8 | 2d93941a-4221-458b-a03d-43bd6315d02e | ex-class-B-1787554251 | 20261 | ACTIVE | 2026-08-24T06:50:53.484Z | 2026-08-24T06:50:53.484Z | `null` |
| 4 | e66feb9d-c8de-40f3-9690-0bdafdcd16bf | 2d93941a-4221-458b-a03d-43bd6315d02e | ex-class-A-1787554624 | 20261 | ACTIVE | 2026-08-24T06:57:05.331Z | 2026-08-24T06:57:05.331Z | `null` |
| 5 | 20c3202d-48a6-45d4-bae8-19493ae4c3fb | 2d93941a-4221-458b-a03d-43bd6315d02e | ex-class-B-1787554624 | 20261 | ACTIVE | 2026-08-24T06:57:06.098Z | 2026-08-24T06:57:06.098Z | `null` |

---

### `docker_images` (0 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `name` | character varying(255) | NOT NULL |
| `image_url` | character varying(500) | NOT NULL |
| `description` | text |  |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |
| `owner_id` | uuid | NOT NULL · default: 00000000-0000-0000-0000-000000... |

> *(Bảng rỗng — chưa có dữ liệu)*

---

### `score_components` (8 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `class_id` | uuid | 🔗 FK · NOT NULL |
| `type` | character varying(30) | NOT NULL |
| `weight` | numeric | NOT NULL |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |

**Dữ liệu mẫu (5 bản ghi):**

| # | `id` | `class_id` | `type` | `weight` | `created_at` | `updated_at` | `deleted_at` |
|---|---|---|---|---|---|---|---|
| 1 | 9062ff74-53f1-4e9d-92be-be306545499a | 19825639-710a-49c6-9072-789f0205aa86 | ATTENDANCE | 0.1000 | 2026-08-23T10:01:03.459Z | 2026-08-23T10:01:03.460Z | `null` |
| 2 | 979d8373-e842-4825-9309-5420d0b6ec67 | 19825639-710a-49c6-9072-789f0205aa86 | EXERCISE | 0.2000 | 2026-08-23T10:01:03.796Z | 2026-08-23T10:01:03.796Z | `null` |
| 3 | 798b4046-5607-4c82-9ef2-da9f0778a6cf | 19825639-710a-49c6-9072-789f0205aa86 | ASSIGNMENT | 0.2000 | 2026-08-23T10:01:04.039Z | 2026-08-23T10:08:02.112Z | 2026-08-23T10:08:02.083Z |
| 4 | 3fd0ab12-202f-42f9-9ebf-40d1b68518e2 | 19825639-710a-49c6-9072-789f0205aa86 | ASSIGNMENT | 0.2000 | 2026-08-23T11:41:20.492Z | 2026-08-23T11:41:20.493Z | `null` |
| 5 | 7f4df12c-6dc1-441b-857b-445ee9979f26 | 19825639-710a-49c6-9072-789f0205aa86 | FINAL_EXAM | 0.5000 | 2026-08-23T10:01:04.288Z | 2026-08-23T11:41:20.797Z | `null` |

---

### `student_scores` (5 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `class_id` | uuid | 🔗 FK · NOT NULL |
| `student_code` | character varying(50) | NOT NULL |
| `component_id` | uuid | 🔗 FK · NOT NULL |
| `score` | numeric | NOT NULL |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |

**Dữ liệu mẫu (5 bản ghi):**

| # | `id` | `class_id` | `student_code` | `component_id` | `score` | `created_at` | `updated_at` | `deleted_at` |
|---|---|---|---|---|---|---|---|---|
| 1 | e30ce938-dc89-41ed-b7d9-a26410387577 | 19825639-710a-49c6-9072-789f0205aa86 | B22DCCN002 | 9062ff74-53f1-4e9d-92be-be306545499a | 0.00 | 2026-08-23T11:41:37.150Z | 2026-08-23T11:41:37.150Z | `null` |
| 2 | 9e6dc377-2e49-4d02-aef7-6aab44344a64 | 19825639-710a-49c6-9072-789f0205aa86 | B22DCCN002 | 3fd0ab12-202f-42f9-9ebf-40d1b68518e2 | 7.00 | 2026-08-23T11:41:37.623Z | 2026-08-23T11:41:37.623Z | `null` |
| 3 | d0fab83d-aa0e-48a3-9b59-00c5fda59a8b | 19825639-710a-49c6-9072-789f0205aa86 | B22DCCN001 | 3fd0ab12-202f-42f9-9ebf-40d1b68518e2 | 10.00 | 2026-09-29T14:58:29.100Z | 2026-09-29T14:58:29.100Z | `null` |
| 4 | e7d052f4-1c98-490c-80ae-19df491d3e1e | 19825639-710a-49c6-9072-789f0205aa86 | B22DCCN001 | 9062ff74-53f1-4e9d-92be-be306545499a | 10.00 | 2026-09-29T14:58:29.697Z | 2026-09-29T14:58:29.697Z | `null` |
| 5 | 703aec83-4977-4a02-a651-49c8e7f264ee | 19825639-710a-49c6-9072-789f0205aa86 | B22DCCN001 | 7f4df12c-6dc1-441b-857b-445ee9979f26 | 10.00 | 2026-09-29T14:58:30.313Z | 2026-09-29T14:58:30.313Z | `null` |

---

### `test_plans` (8 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `assignment_id` | uuid | 🔗 FK · NOT NULL |
| `name` | character varying(255) | NOT NULL |
| `description` | text |  |
| `sequence_order` | integer | NOT NULL · default: 0 |
| `weight` | integer | NOT NULL · default: 1 |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |

**Dữ liệu mẫu (5 bản ghi):**

| # | `id` | `assignment_id` | `name` | `description` | `sequence_order` | `weight` | `created_at` | `updated_at` | `deleted_at` |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 2d83bb1f-ec5c-4497-b581-c55613acac9d | ff860f4e-b77e-480c-b52f-10680f3c912a | CRUD Book API — Advanced | `null` | 2 | 5 | 2026-08-24T09:10:15.115Z | 2026-08-24T09:10:15.115Z | `null` |
| 2 | 4819c97e-818c-402d-9291-676f37916143 | ff860f4e-b77e-480c-b52f-10680f3c912a | CRUD Book API — v2 | basic flow | 3 | 10 | 2026-08-24T09:10:11.078Z | 2026-08-24T09:10:39.986Z | `null` |
| 3 | 215259ad-0591-43e3-821f-efd3d699b66f | 2b01f38b-24e3-46d4-9e6f-2ea8cfe9ad52 | CRUD Book API — Advanced | `null` | 2 | 5 | 2026-08-24T09:11:20.365Z | 2026-08-24T09:11:20.365Z | `null` |
| 4 | 25309a1b-497c-435b-b585-3c7f3d058fbb | 2b01f38b-24e3-46d4-9e6f-2ea8cfe9ad52 | CRUD Book API — v2 | basic flow | 3 | 10 | 2026-08-24T09:11:18.145Z | 2026-08-24T09:11:51.126Z | `null` |
| 5 | 39b3448d-163b-4fac-9eb0-2d8550eb9584 | 76b359be-80f6-4e6a-bcbe-4643a57a8d07 | CRUD Book API — Advanced | `null` | 2 | 5 | 2026-08-27T01:05:55.386Z | 2026-08-27T01:05:55.386Z | `null` |

---

### `test_steps` (36 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `plan_id` | uuid | 🔗 FK · NOT NULL |
| `step_order` | integer | NOT NULL |
| `name` | character varying(255) | NOT NULL |
| `step_type` | character varying(50) | NOT NULL |
| `config` | jsonb | NOT NULL · default: {} |
| `expected_result` | jsonb |  |
| `weight` | integer | NOT NULL · default: 1 |
| `timeout_ms` | integer | default: 30000 |
| `is_required` | boolean | NOT NULL · default: true |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |
| `description` | text |  |

**Dữ liệu mẫu (5 bản ghi):**

| # | `id` | `plan_id` | `step_order` | `name` | `step_type` | `config` | `expected_result` | `weight` | `timeout_ms` | `is_required` | `created_at` | `updated_at` | `deleted_at` | `description` |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | cb1f1cb1-552f-4ba9-b19a-177cd1835de4 | 4819c97e-818c-402d-9291-676f37916143 | 1 | Create a book | HTTP_REQUEST | `{"body":{"year":1941,"title":"Dế Mèn Phiêu Lưu Ký","author":"Tô Hoài"},"path"...` | `null` | 2 | `null` | true | 2026-08-24T09:10:17.849Z | 2026-08-24T09:10:17.849Z | `null` | `null` |
| 2 | 8118b117-f530-4885-8c1a-bfae42e72dd1 | 4819c97e-818c-402d-9291-676f37916143 | 3 | Search by title | HTTP_REQUEST | `{"path":"/api/v1/books","method":"GET","query_params":{"title":"Dế Mèn"},"exp...` | `null` | 2 | `null` | true | 2026-08-24T09:10:21.242Z | 2026-08-24T09:10:21.242Z | `null` | `null` |
| 3 | 4e109937-9c75-486b-9ec4-912cafd409d4 | 4819c97e-818c-402d-9291-676f37916143 | 4 | Check DB schema | DB_SCHEMA_CHECK | `{"checks":[{"kind":"TABLE_EXISTS","table_name":"books"},{"kind":"COLUMN_EXIST...` | `null` | 2 | `null` | true | 2026-08-24T09:10:22.581Z | 2026-08-24T09:10:22.581Z | `null` | `null` |
| 4 | cd40ef12-d1e1-4d59-af4b-9bbc52819233 | 4819c97e-818c-402d-9291-676f37916143 | 5 | Verify data in DB | DB_QUERY | `{"query":"SELECT title, author, year FROM books WHERE id = ${bookId}","expect...` | {"columns":["title","author","year"],"row_count":1} | 2 | 15000 | true | 2026-08-24T09:10:23.841Z | 2026-08-24T09:10:23.841Z | `null` | `null` |
| 5 | fbd68a1b-7471-46d9-8fa2-3c346533bdec | 4819c97e-818c-402d-9291-676f37916143 | 2 | Verify book details v2 | HTTP_REQUEST | `{"path":"/api/v1/books/${bookId}","method":"GET","expected_status":200,"expec...` | `null` | 2 | `null` | true | 2026-08-24T09:10:19.679Z | 2026-08-24T09:10:37.121Z | `null` | `null` |

---

## 🗄️ `submission_db` — submission-service

> Quản lý bài nộp của sinh viên. File ZIP lưu trên RustFS. 1 Flyway migration.

### Tổng quan bảng

| Bảng | Số bản ghi |
|------|-----------|
| `submissions` | 🟢 57 |

---

### `submissions` (57 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL · default: gen_random_uuid() |
| `assignment_id` | uuid | NOT NULL |
| `student_id` | uuid | NOT NULL |
| `plan_id` | uuid |  |
| `rustfs_path` | text | NOT NULL |
| `zip_file_name` | text |  |
| `status` | character varying(20) | NOT NULL · default: PENDING |
| `latest` | boolean | NOT NULL · default: true |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |

**Dữ liệu mẫu (5 bản ghi):**

| # | `id` | `assignment_id` | `student_id` | `plan_id` | `rustfs_path` | `zip_file_name` | `status` | `latest` | `created_at` | `updated_at` | `deleted_at` |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | f643adc4-85cb-4c77-acea-d9d8177fcabe | 76b359be-80f6-4e6a-bcbe-4643a57a8d07 | 515cb07a-cc73-4fb0-b57c-9d615f35261d | c20f3182-825b-48b2-be92-e848c1538810 | submissions/f643adc4-85cb-4c77-acea-d9d8177fcabe.zip | book-app-test.zip | DONE | true | 2026-09-19T07:48:48.117Z | 2026-09-19T07:52:56.377Z | `null` |
| 2 | 12791ed8-be50-4c06-9340-c68f851755ff | 76b359be-80f6-4e6a-bcbe-4643a57a8d07 | fe9ec2b7-24f0-48c3-8a5a-b1a2c35f4779 | c20f3182-825b-48b2-be92-e848c1538810 | submissions/12791ed8-be50-4c06-9340-c68f851755ff.zip | book-app-test.zip | DONE | true | 2026-09-19T08:24:50.525Z | 2026-09-19T08:27:25.960Z | `null` |
| 3 | 295de4a6-935b-40a3-a919-7118a923a80a | 76b359be-80f6-4e6a-bcbe-4643a57a8d07 | 94fbeeac-61d0-4ebd-a4a5-a467d264e59c | c20f3182-825b-48b2-be92-e848c1538810 | submissions/295de4a6-935b-40a3-a919-7118a923a80a.zip | book-app-test.zip | DONE | true | 2026-09-19T08:24:57.566Z | 2026-09-19T08:30:32.934Z | `null` |
| 4 | c7882616-9f03-4123-a1f0-7d63e4beb1fe | 769cbcb8-fffa-4b54-a54b-01c796e26ddc | e153f51b-962c-4a86-837e-b4e0befc5f76 | `null` | submissions/c7882616-9f03-4123-a1f0-7d63e4beb1fe.zip | kpdll.zip | PENDING | true | 2026-08-18T04:44:58.796Z | 2026-08-18T04:44:58.796Z | `null` |
| 5 | 315c9a8a-3a3e-4b70-a39c-55c36c983ce3 | 769cbcb8-fffa-4b54-a54b-01c796e26ddc | dbb40c84-67a5-4148-82f5-6d9862d2e44c | `null` | submissions/315c9a8a-3a3e-4b70-a39c-55c36c983ce3.zip | kpdll.zip | PENDING | true | 2026-08-18T06:38:12.902Z | 2026-08-18T06:38:12.902Z | `null` |

---

## 🗄️ `result_db` — result-service

> Lưu kết quả chấm thi tổng hợp sau khi executor hoàn thành. 3 Flyway migrations.

### Tổng quan bảng

| Bảng | Số bản ghi |
|------|-----------|
| `manual_scores` | ⚪ 0 |
| `results` | 🟢 26 |
| `step_results` | 🟢 104 |

---

### `manual_scores` (0 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `class_id` | uuid | NOT NULL |
| `student_code` | character varying(50) | NOT NULL |
| `assignment_id` | uuid |  |
| `score` | numeric | NOT NULL |
| `comment` | text |  |
| `created_by` | uuid | NOT NULL |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |

> *(Bảng rỗng — chưa có dữ liệu)*

---

### `results` (26 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `submission_id` | uuid | NOT NULL |
| `assignment_id` | uuid | NOT NULL |
| `student_id` | uuid | NOT NULL |
| `plan_id` | uuid |  |
| `score` | numeric | NOT NULL |
| `max_score` | numeric | NOT NULL · default: 10.00 |
| `status` | character varying(20) | NOT NULL · default: PENDING |
| `summary_log` | text |  |
| `is_latest` | boolean | NOT NULL · default: true |
| `started_at` | timestamp with time zone |  |
| `completed_at` | timestamp with time zone |  |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |
| `plan_weight` | integer |  |

**Dữ liệu mẫu (5 bản ghi):**

| # | `id` | `submission_id` | `assignment_id` | `student_id` | `plan_id` | `score` | `max_score` | `status` | `summary_log` | `is_latest` | `started_at` | `completed_at` | `created_at` | `updated_at` | `deleted_at` | `plan_weight` |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 9aafbbe4-810e-4ebe-b2a0-598bfd42bf2d | 829901c2-4649-4c79-9699-3941af455d7b | c2d9c0f0-6c4e-4334-aaa0-8374ee52c5f0 | 6b0a33cc-0f47-443b-ac84-f31065463cc0 | 0849b605-c769-4683-a112-65b34a2ec454 | 10.00 | 10.00 | DONE | `null` | true | `null` | 2026-09-13T10:24:44.671Z | 2026-09-13T10:24:44.673Z | 2026-09-13T10:24:44.673Z | `null` | `null` |
| 2 | c4373880-eaaa-4f25-abd5-cf6318b40ae1 | 4f44fa7d-3556-4c98-b441-8eca53d25e19 | c2d9c0f0-6c4e-4334-aaa0-8374ee52c5f0 | 92a92e03-e116-4f61-b870-42b60b0adffe | 0849b605-c769-4683-a112-65b34a2ec454 | 10.00 | 10.00 | DONE | test | true | `null` | 2026-09-13T10:38:00.784Z | 2026-09-13T10:38:00.787Z | 2026-09-13T10:38:00.787Z | `null` | 10 |
| 3 | 5af74e06-b52c-425b-8ca9-26a464f4cc8b | 0fc5eacb-f862-47c7-8b5b-2cbc2d6d5fe1 | c2d9c0f0-6c4e-4334-aaa0-8374ee52c5f0 | aacdfbf4-41cb-4a5e-8f80-26ef562fcb48 | 0849b605-c769-4683-a112-65b34a2ec454 | 10.00 | 10.00 | DONE | Passed 2/2 steps (100%), Score: 10.00/10 | true | `null` | 2026-09-13T10:51:54.698Z | 2026-09-13T10:51:54.789Z | 2026-09-13T10:51:54.789Z | `null` | 10 |
| 4 | fd4085d0-4986-498d-870b-b9e5b8de0f52 | e3f64df5-1d93-462c-a9cc-130df37c03e4 | 76b359be-80f6-4e6a-bcbe-4643a57a8d07 | fe48e137-18b5-4550-b3f2-21f071743897 | c20f3182-825b-48b2-be92-e848c1538810 | 0.00 | 10.00 | FAILED | Grading infrastructure failed: Container startup failed for image docker:25.0.5 | true | `null` | 2026-09-17T14:35:52.651Z | 2026-09-17T14:35:52.754Z | 2026-09-17T14:35:52.754Z | `null` | 1 |
| 5 | d3451b53-2244-4a99-b85c-fc6d0cb7e90b | f8f14757-1538-481d-a65f-7b40ca4ee58a | 76b359be-80f6-4e6a-bcbe-4643a57a8d07 | 21c2572d-9488-47e6-a486-f9cec3600409 | c20f3182-825b-48b2-be92-e848c1538810 | 0.00 | 10.00 | FAILED | Grading infrastructure failed: Container startup failed for image docker:25.0.5 | true | `null` | 2026-09-17T15:36:29.149Z | 2026-09-17T15:36:29.150Z | 2026-09-17T15:36:29.150Z | `null` | 1 |

---

### `step_results` (104 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `result_id` | uuid | 🔗 FK · NOT NULL |
| `plan_id` | uuid |  |
| `step_id` | uuid |  |
| `step_order` | integer | NOT NULL |
| `step_name` | character varying(255) | NOT NULL |
| `step_type` | character varying(50) | NOT NULL |
| `passed` | boolean | NOT NULL |
| `weight` | integer | NOT NULL · default: 1 |
| `score` | numeric | NOT NULL · default: 0 |
| `actual_value` | text |  |
| `expected_value` | text |  |
| `error_message` | text |  |
| `duration_ms` | integer |  |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |

**Dữ liệu mẫu (5 bản ghi):**

| # | `id` | `result_id` | `plan_id` | `step_id` | `step_order` | `step_name` | `step_type` | `passed` | `weight` | `score` | `actual_value` | `expected_value` | `error_message` | `duration_ms` | `created_at` | `updated_at` | `deleted_at` |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 14044053-9f56-44d1-83d7-ff474134d6f1 | 5af74e06-b52c-425b-8ca9-26a464f4cc8b | 0849b605-c769-4683-a112-65b34a2ec454 | c76b67bf-f5e4-438b-90ae-e92081f1a97e | 1 | Create Book | HTTP_REQUEST | true | 1 | 1.00 | `{"author":"Test Author","id":"9ce7c393-fcf4-43bf-b0bb-d2120264fe32","isbn":"1...` | `null` | `null` | 499 | 2026-09-13T10:51:55.067Z | 2026-09-13T10:51:55.067Z | `null` |
| 2 | 221c8b1b-fd4a-4850-9100-069f20412749 | 5af74e06-b52c-425b-8ca9-26a464f4cc8b | 0849b605-c769-4683-a112-65b34a2ec454 | 66ad6084-b252-4492-8677-6947d055a3b2 | 2 | Get Book | HTTP_REQUEST | true | 1 | 1.00 | `<!doctype html>
<html lang=en>
<title>404 Not Found</title>
<h1>Not Found</h1...` | `null` | `null` | 493 | 2026-09-13T10:51:55.299Z | 2026-09-13T10:51:55.299Z | `null` |
| 3 | 9f77ff3f-373f-45bb-9ffd-2f325f6c6788 | ed6ad5a5-8650-4033-b42d-5632bb9684df | c20f3182-825b-48b2-be92-e848c1538810 | 1d768a78-be3f-4448-931d-258f436cfb98 | 1 | Create a book | HTTP_REQUEST | true | 2 | 2.00 | `{"id":"a963d4bb-6098-4de8-993b-e58d30d5d9e6","title":"Dế Mèn Phiêu Lưu Ký","a...` | `null` | `null` | 4360 | 2026-09-19T02:57:20.935Z | 2026-09-19T02:57:20.935Z | `null` |
| 4 | 51c177db-77d9-458d-9465-df104d03a476 | ed6ad5a5-8650-4033-b42d-5632bb9684df | c20f3182-825b-48b2-be92-e848c1538810 | a4c94b8d-4cf3-4ca8-bd21-67949a00dc2f | 2 | Verify book details v2 | HTTP_REQUEST | true | 2 | 2.00 | `{"id":"a963d4bb-6098-4de8-993b-e58d30d5d9e6","title":"Dế Mèn Phiêu Lưu Ký","a...` | `null` | `null` | 1684 | 2026-09-19T02:57:21.170Z | 2026-09-19T02:57:21.170Z | `null` |
| 5 | 189b51a6-e716-4107-99ed-e6e9f3570618 | ed6ad5a5-8650-4033-b42d-5632bb9684df | c20f3182-825b-48b2-be92-e848c1538810 | 27812688-290e-429a-b36d-f99153d4620d | 4 | Check DB schema | DB_SCHEMA_CHECK | false | 2 | 0.00 | `null` | `null` | Unknown step type: DB_SCHEMA_CHECK | `null` | 2026-09-19T02:57:21.399Z | 2026-09-19T02:57:21.399Z | `null` |

---

## 🗄️ `executor_db` — executor-service

> Điều phối quá trình chấm thi theo Saga pattern. Lưu log và raw HTTP test results. 7 Flyway migrations.

### Tổng quan bảng

| Bảng | Số bản ghi |
|------|-----------|
| `docker_image_state` | ⚪ 0 |
| `grading_jobs` | 🟢 40 |
| `grading_logs` | 🟢 232 |
| `grading_saga_steps` | 🟢 329 |
| `grading_sagas` | 🟢 44 |
| `grading_step_results` | 🟢 139 |

---

### `docker_image_state` (0 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `image_url` | character varying(500) | NOT NULL |
| `pod_id` | character varying(255) | NOT NULL |
| `status` | character varying(20) | NOT NULL |
| `last_checked_at` | timestamp with time zone |  |
| `last_pulled_at` | timestamp with time zone |  |
| `error_message` | text |  |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |

> *(Bảng rỗng — chưa có dữ liệu)*

---

### `grading_jobs` (40 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `submission_id` | uuid | NOT NULL |
| `assignment_id` | uuid | NOT NULL |
| `student_id` | uuid | NOT NULL |
| `plan_id` | uuid |  |
| `status` | character varying(20) | NOT NULL · default: PENDING |
| `retry_count` | integer | NOT NULL · default: 0 |
| `error_message` | text |  |
| `started_at` | timestamp with time zone |  |
| `completed_at` | timestamp with time zone |  |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |
| `rustfs_path` | text |  |
| `trace_id` | character varying(100) |  |

**Dữ liệu mẫu (5 bản ghi):**

| # | `id` | `submission_id` | `assignment_id` | `student_id` | `plan_id` | `status` | `retry_count` | `error_message` | `started_at` | `completed_at` | `created_at` | `updated_at` | `deleted_at` | `rustfs_path` | `trace_id` |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 75e9e64f-d5a3-41cf-98be-98bc6223914c | 74e1959d-863d-42f4-a950-0e133b6bc901 | 76b359be-80f6-4e6a-bcbe-4643a57a8d07 | 67243de3-73ae-44de-9bb5-5ae874996f2c | c20f3182-825b-48b2-be92-e848c1538810 | DONE | 0 | `null` | 2026-09-19T09:14:23.215Z | 2026-09-19T09:16:01.352Z | 2026-09-19T08:41:07.358Z | 2026-09-19T09:16:01.592Z | `null` | submissions/74e1959d-863d-42f4-a950-0e133b6bc901.zip | `null` |
| 2 | 881d8918-23e9-4fd0-966d-3b73da810d05 | 734d9d45-31cc-41d1-9fe7-9ba04fac8c23 | 76b359be-80f6-4e6a-bcbe-4643a57a8d07 | ee1dc886-3061-4ba7-afce-625999465f17 | c20f3182-825b-48b2-be92-e848c1538810 | FAILED | 0 | `Grading infrastructure failed: Local Docker Compose exited abnormally with co...` | 2026-09-13T02:08:38.780Z | 2026-09-13T02:09:18.203Z | 2026-09-13T02:08:33.391Z | 2026-09-13T02:09:18.464Z | `null` | submissions/734d9d45-31cc-41d1-9fe7-9ba04fac8c23.zip | `null` |
| 3 | 1d66a642-5c47-49e6-82a0-a15633fbc687 | 2b8c110c-10e0-40c4-b34f-9153f5dd5128 | 76b359be-80f6-4e6a-bcbe-4643a57a8d07 | 6395abb7-33c9-4119-a74f-ca92147c3b9b | c20f3182-825b-48b2-be92-e848c1538810 | DONE | 0 | `null` | 2026-09-19T02:08:47.862Z | 2026-09-19T02:12:11.035Z | 2026-09-19T02:08:42.289Z | 2026-09-19T02:12:11.278Z | `null` | submissions/2b8c110c-10e0-40c4-b34f-9153f5dd5128.zip | `null` |
| 4 | 8a257557-f388-4a86-9eaf-3df3553ec17a | f909d996-a918-42ff-9c20-8b8d6d22b03e | 76b359be-80f6-4e6a-bcbe-4643a57a8d07 | 7a9f527a-7b41-49ea-ad1e-f5ab9b0b2d15 | c20f3182-825b-48b2-be92-e848c1538810 | FAILED | 0 | `Grading infrastructure failed: Local Docker Compose exited abnormally with co...` | 2026-09-13T08:05:23.916Z | 2026-09-13T08:05:46.734Z | 2026-09-13T08:05:20.143Z | 2026-09-13T08:05:46.989Z | `null` | submissions/f909d996-a918-42ff-9c20-8b8d6d22b03e.zip | `null` |
| 5 | af826f05-9b69-4c25-ad84-2e9801faa63b | d924ff22-0f94-4a45-8dd1-1beed77e3de6 | 76b359be-80f6-4e6a-bcbe-4643a57a8d07 | d906cc88-ab70-4d41-aaa2-dc76a4ffb35e | c20f3182-825b-48b2-be92-e848c1538810 | DONE | 0 | `null` | 2026-09-19T02:54:23.461Z | 2026-09-19T02:57:14.829Z | 2026-09-19T02:54:18.555Z | 2026-09-19T02:57:15.063Z | `null` | submissions/d924ff22-0f94-4a45-8dd1-1beed77e3de6.zip | `null` |

---

### `grading_logs` (232 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `job_id` | uuid | 🔗 FK · NOT NULL |
| `submission_id` | uuid | NOT NULL |
| `step` | character varying(100) |  |
| `message` | text | NOT NULL |
| `level` | character varying(10) | NOT NULL · default: INFO |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |

**Dữ liệu mẫu (5 bản ghi):**

| # | `id` | `job_id` | `submission_id` | `step` | `message` | `level` | `created_at` | `updated_at` | `deleted_at` |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 2568b7f6-95f7-4e58-bdff-56bcf19ea5a4 | 881d8918-23e9-4fd0-966d-3b73da810d05 | 734d9d45-31cc-41d1-9fe7-9ba04fac8c23 | RECEIVED | Grading job received from wgs-events (traceId=null) | INFO | 2026-09-13T02:08:34.791Z | 2026-09-13T02:08:34.791Z | `null` |
| 2 | ee219243-5293-4398-872c-1a590dd78112 | 881d8918-23e9-4fd0-966d-3b73da810d05 | 734d9d45-31cc-41d1-9fe7-9ba04fac8c23 | GRADING | FETCHING: Fetching grading config and artifact | INFO | 2026-09-13T02:08:39.568Z | 2026-09-13T02:08:39.568Z | `null` |
| 3 | 9e729980-df95-4584-9785-f8cd90a00bd2 | 881d8918-23e9-4fd0-966d-3b73da810d05 | 734d9d45-31cc-41d1-9fe7-9ba04fac8c23 | GRADING | BUILDING: Downloading submission and booting app | INFO | 2026-09-13T02:08:50.832Z | 2026-09-13T02:08:50.833Z | `null` |
| 4 | 2fb13502-ca2c-40a6-9c40-3e2efac363bf | 881d8918-23e9-4fd0-966d-3b73da810d05 | 734d9d45-31cc-41d1-9fe7-9ba04fac8c23 | GRADING | `Grading infrastructure failed: Local Docker Compose exited abnormally with co...` | ERROR | 2026-09-13T02:09:18.995Z | 2026-09-13T02:09:18.995Z | `null` |
| 5 | 5900be04-9013-48f9-9dce-da37d6a3badd | 8a257557-f388-4a86-9eaf-3df3553ec17a | f909d996-a918-42ff-9c20-8b8d6d22b03e | RECEIVED | Grading job received from wgs-events (traceId=null) | INFO | 2026-09-13T08:05:20.713Z | 2026-09-13T08:05:20.713Z | `null` |

---

### `grading_saga_steps` (329 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `saga_id` | uuid | 🔗 FK · NOT NULL |
| `step_name` | character varying(300) | NOT NULL |
| `plan_id` | uuid |  |
| `step_id` | uuid |  |
| `status` | character varying(20) | NOT NULL · default: STARTED |
| `error_message` | text |  |
| `started_at` | timestamp with time zone | NOT NULL · default: now() |
| `completed_at` | timestamp with time zone |  |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |

**Dữ liệu mẫu (5 bản ghi):**

| # | `id` | `saga_id` | `step_name` | `plan_id` | `step_id` | `status` | `error_message` | `started_at` | `completed_at` | `created_at` | `updated_at` | `deleted_at` |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | e2fa47d9-4381-4be5-92dd-8afc2f5cbff9 | 58d68727-ff7b-416a-9d2d-9a2cc91a37d8 | FETCH_CONFIG | c20f3182-825b-48b2-be92-e848c1538810 | `null` | DONE | `null` | 2026-09-13T02:08:41.874Z | 2026-09-13T02:08:49.337Z | 2026-09-13T02:08:41.878Z | 2026-09-13T02:08:49.585Z | `null` |
| 2 | f393de84-2a4f-40ba-bade-14181f387847 | 58d68727-ff7b-416a-9d2d-9a2cc91a37d8 | DOWNLOAD_ARTIFACT | c20f3182-825b-48b2-be92-e848c1538810 | `null` | DONE | `null` | 2026-09-13T02:08:52.659Z | 2026-09-13T02:08:55.609Z | 2026-09-13T02:08:52.662Z | 2026-09-13T02:08:55.858Z | `null` |
| 3 | fc3e0e81-e7a1-4354-9fc8-9dca2445af58 | 58d68727-ff7b-416a-9d2d-9a2cc91a37d8 | BOOT_COMPOSE | c20f3182-825b-48b2-be92-e848c1538810 | `null` | FAILED | `Local Docker Compose exited abnormally with code 1 whilst running command: co...` | 2026-09-13T02:08:57.621Z | 2026-09-13T02:09:17.453Z | 2026-09-13T02:08:57.625Z | 2026-09-13T02:09:17.712Z | `null` |
| 4 | 9627010c-d2d2-490e-8004-a3f40f6b9aca | 8c7a9c29-33bc-4cb1-96fd-4573cf4d6bba | FETCH_CONFIG | c20f3182-825b-48b2-be92-e848c1538810 | `null` | DONE | `null` | 2026-09-13T08:05:26.718Z | 2026-09-13T08:05:31.695Z | 2026-09-13T08:05:26.746Z | 2026-09-13T08:05:31.938Z | `null` |
| 5 | b6947c13-1cc7-479b-a1d1-f3c19960ce63 | 8c7a9c29-33bc-4cb1-96fd-4573cf4d6bba | DOWNLOAD_ARTIFACT | c20f3182-825b-48b2-be92-e848c1538810 | `null` | DONE | `null` | 2026-09-13T08:05:34.696Z | 2026-09-13T08:05:37.154Z | 2026-09-13T08:05:34.701Z | 2026-09-13T08:05:37.382Z | `null` |

---

### `grading_sagas` (44 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `job_id` | uuid | 🔗 FK · NOT NULL |
| `saga_type` | character varying(50) | NOT NULL · default: GRADE_SUBMISSION |
| `status` | character varying(20) | NOT NULL · default: STARTED |
| `current_step` | character varying(100) |  |
| `started_at` | timestamp with time zone | NOT NULL · default: now() |
| `completed_at` | timestamp with time zone |  |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |

**Dữ liệu mẫu (5 bản ghi):**

| # | `id` | `job_id` | `saga_type` | `status` | `current_step` | `started_at` | `completed_at` | `created_at` | `updated_at` | `deleted_at` |
|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 33590fd0-0ed7-4856-b0dd-21f34945e0ae | d1076986-ccb8-444e-8d8f-7c98acdd0fce | GRADE_SUBMISSION | DONE | SCORE_REPORT | 2026-09-27T13:04:16.048Z | 2026-09-27T13:06:13.582Z | 2026-09-27T13:04:16.058Z | 2026-09-27T13:06:13.821Z | `null` |
| 2 | 8912ebb0-367c-4c28-87cc-8ec98f625a07 | 47b49aff-5060-404a-a57a-930c715cb122 | GRADE_SUBMISSION | DONE | SCORE_REPORT | 2026-09-19T08:35:58.044Z | 2026-09-19T08:37:46.101Z | 2026-09-19T08:35:58.046Z | 2026-09-19T08:37:46.429Z | `null` |
| 3 | 5cc4d902-be8e-4104-ad2b-1c214d2dfcf7 | 93410ba4-08b0-44a1-bab8-294d537facd8 | GRADE_SUBMISSION | DONE | SCORE_REPORT | 2026-09-13T10:23:44.991Z | 2026-09-13T10:24:36.759Z | 2026-09-13T10:23:45.020Z | 2026-09-13T10:24:36.994Z | `null` |
| 4 | 58d68727-ff7b-416a-9d2d-9a2cc91a37d8 | 881d8918-23e9-4fd0-966d-3b73da810d05 | GRADE_SUBMISSION | FAILED | BOOT_COMPOSE | 2026-09-13T02:08:40.065Z | 2026-09-13T02:09:19.999Z | 2026-09-13T02:08:40.071Z | 2026-09-13T02:09:20.252Z | `null` |
| 5 | 8c7a9c29-33bc-4cb1-96fd-4573cf4d6bba | 8a257557-f388-4a86-9eaf-3df3553ec17a | GRADE_SUBMISSION | FAILED | BOOT_COMPOSE | 2026-09-13T08:05:25.087Z | 2026-09-13T08:05:48.380Z | 2026-09-13T08:05:25.102Z | 2026-09-13T08:05:48.607Z | `null` |

---

### `grading_step_results` (139 bản ghi)

**Cấu trúc:**

| Cột | Kiểu | Ghi chú |
|-----|------|---------|
| `id` | uuid | 🔑 PK · NOT NULL |
| `job_id` | uuid | 🔗 FK · NOT NULL |
| `plan_id` | uuid | NOT NULL |
| `step_id` | uuid | NOT NULL |
| `step_order` | integer | NOT NULL |
| `step_name` | character varying(255) | NOT NULL |
| `step_type` | character varying(50) | NOT NULL |
| `status` | character varying(20) | NOT NULL |
| `actual_status_code` | integer |  |
| `request_url` | text |  |
| `request_headers` | jsonb |  |
| `request_body` | text |  |
| `response_status_code` | integer |  |
| `response_headers` | jsonb |  |
| `response_body` | text |  |
| `expected_status_code` | integer |  |
| `expected_response_body` | text |  |
| `extracted_variables` | jsonb |  |
| `assertion_result` | jsonb |  |
| `error_message` | text |  |
| `duration_ms` | integer |  |
| `started_at` | timestamp with time zone | NOT NULL · default: now() |
| `completed_at` | timestamp with time zone |  |
| `created_at` | timestamp with time zone | NOT NULL · default: now() |
| `updated_at` | timestamp with time zone | NOT NULL · default: now() |
| `deleted_at` | timestamp with time zone |  |

**Dữ liệu mẫu (5 bản ghi):**

| # | `id` | `job_id` | `plan_id` | `step_id` | `step_order` | `step_name` | `step_type` | `status` | `actual_status_code` | `request_url` | `request_headers` | `request_body` | `response_status_code` | `response_headers` | `response_body` | `expected_status_code` | `expected_response_body` | `extracted_variables` | `assertion_result` | `error_message` | `duration_ms` | `started_at` | `completed_at` | `created_at` | `updated_at` | `deleted_at` |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| 1 | 1249d590-3d7e-44c8-a693-bcda1eef48ef | b812b3d5-a678-478e-bcdd-d78333e7cbcf | 0849b605-c769-4683-a112-65b34a2ec454 | c76b67bf-f5e4-438b-90ae-e92081f1a97e | 1 | Create Book | HTTP_REQUEST | PASSED | 201 | http://localhost:32776/api/v1/books | {} | {"isbn":"1234567890","title":"Test Book","author":"Test Author"} | 201 | `{"date":"Sun, 13 Sep 2026 10:16:31 GMT","server":"Werkzeug/3.1.8 Python/3.11....` | `{"author":"Test Author","id":"f82e3515-cf7f-4655-b920-65d2d811357a","isbn":"1...` | `null` | `null` | `null` | [] | `null` | 1365 | 2026-09-13T10:16:31.165Z | 2026-09-13T10:16:32.529Z | 2026-09-13T10:16:32.569Z | 2026-09-13T10:16:32.569Z | `null` |
| 2 | 090ed2da-d096-4f2a-9b7b-19727ac48f35 | b812b3d5-a678-478e-bcdd-d78333e7cbcf | 0849b605-c769-4683-a112-65b34a2ec454 | 66ad6084-b252-4492-8677-6947d055a3b2 | 2 | Get Book | HTTP_REQUEST | PASSED | 404 | http://localhost:32776/api/v1/books/ | {} | `null` | 404 | `{"date":"Sun, 13 Sep 2026 10:16:36 GMT","server":"Werkzeug/3.1.8 Python/3.11....` | `<!doctype html>
<html lang=en>
<title>404 Not Found</title>
<h1>Not Found</h1...` | `null` | `null` | `null` | [] | `null` | 529 | 2026-09-13T10:16:36.170Z | 2026-09-13T10:16:36.699Z | 2026-09-13T10:16:36.706Z | 2026-09-13T10:16:36.707Z | `null` |
| 3 | 116af3a3-9456-4942-8b20-92ef3d21d63e | 93410ba4-08b0-44a1-bab8-294d537facd8 | 0849b605-c769-4683-a112-65b34a2ec454 | c76b67bf-f5e4-438b-90ae-e92081f1a97e | 1 | Create Book | HTTP_REQUEST | PASSED | 201 | http://localhost:32778/api/v1/books | {} | {"isbn":"1234567890","title":"Test Book","author":"Test Author"} | 201 | `{"date":"Sun, 13 Sep 2026 10:24:20 GMT","server":"Werkzeug/3.1.8 Python/3.11....` | `{"author":"Test Author","id":"eb06c350-f6d8-4c48-bc8c-e7f33b9ecaf6","isbn":"1...` | `null` | `null` | `null` | [] | `null` | 892 | 2026-09-13T10:24:20.603Z | 2026-09-13T10:24:21.495Z | 2026-09-13T10:24:21.498Z | 2026-09-13T10:24:21.498Z | `null` |
| 4 | 9553f252-0728-48f8-8a24-586f5e1ae0d2 | 93410ba4-08b0-44a1-bab8-294d537facd8 | 0849b605-c769-4683-a112-65b34a2ec454 | 66ad6084-b252-4492-8677-6947d055a3b2 | 2 | Get Book | HTTP_REQUEST | PASSED | 404 | http://localhost:32778/api/v1/books/ | {} | `null` | 404 | `{"date":"Sun, 13 Sep 2026 10:24:24 GMT","server":"Werkzeug/3.1.8 Python/3.11....` | `<!doctype html>
<html lang=en>
<title>404 Not Found</title>
<h1>Not Found</h1...` | `null` | `null` | `null` | [] | `null` | 483 | 2026-09-13T10:24:24.921Z | 2026-09-13T10:24:25.404Z | 2026-09-13T10:24:25.413Z | 2026-09-13T10:24:25.413Z | `null` |
| 5 | 67dc3d80-f1bd-47ea-9d6b-89bc0fbcf926 | fc7d7f58-e350-4ad5-9908-89a404292e91 | 0849b605-c769-4683-a112-65b34a2ec454 | c76b67bf-f5e4-438b-90ae-e92081f1a97e | 1 | Create Book | HTTP_REQUEST | PASSED | 201 | http://localhost:32780/api/v1/books | {} | {"isbn":"1234567890","title":"Test Book","author":"Test Author"} | 201 | `{"date":"Sun, 13 Sep 2026 10:37:27 GMT","server":"Werkzeug/3.1.8 Python/3.11....` | `{"author":"Test Author","id":"a2cc2238-6943-435a-aa76-ae09795e899b","isbn":"1...` | `null` | `null` | `null` | [] | `null` | 773 | 2026-09-13T10:37:27.456Z | 2026-09-13T10:37:28.229Z | 2026-09-13T10:37:28.234Z | 2026-09-13T10:37:28.234Z | `null` |

---

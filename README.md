# Web Grading System - Source Code Mono-Repo

Mono-repository chứa toàn bộ source code của các microservices trong hệ thống Web Grading System.

## 🏗️ Cấu trúc

```
src-services/
├── .github/
│   └── workflows/
│       └── build-services.yml    # CI/CD workflow tự động detect và build services
├── submission-service/           # Service xử lý submissions
├── executor-service/             # Service thực thi code
├── api-gateway/                  # API Gateway
├── result-service/               # Service quản lý kết quả
├── course-service/               # Service quản lý lớp học, bài tập, điểm số
```

## 🚀 CI/CD Pipeline

### Cách hoạt động

1. **Detect Changes**: Workflow tự động phát hiện service nào thay đổi
2. **Smart Build**: Chỉ build các services bị ảnh hưởng
4. **Docker Push**: Build và push Docker image lên DockerHub
5. **Config Update**: Tự động cập nhật version trong config repo

### Branch Strategy

- **main**: Production release với semantic versioning (`v1.0.0`, `v1.0.1`, ...)
- **develop**: Development builds
- **feat/\***: Feature branches với tag dạng `feat-feature-name-<commit-hash>`

### Versioning

Mỗi service có version riêng theo format: `<service-name>-v<version>`

Ví dụ:
- `submission-service-v1.0.0`
- `api-gateway-v1.2.3`

### Docker Images

Images được push lên DockerHub với naming convention:
```
<dockerhub-username>/web-grading-system-<service-name>:<version>
<dockerhub-username>/web-grading-system-<service-name>:latest
```

## 🔧 Setup

### Prerequisites

- JDK 21
- Maven 3.8+
- Docker (nếu build local)

### Build Local

```bash
# Build một service cụ thể
cd submission-service
mvn clean package

# Build Docker image
docker build -t web-grading-system-submission-service:dev .
```

### GitHub Secrets Required

Cấu hình các secrets sau trong GitHub repository:

- `DOCKERHUB_USERNAME`: Username DockerHub
- `DOCKERHUB_TOKEN`: DockerHub access token
- `CONFIG_REPO_TOKEN`: GitHub Personal Access Token có quyền write vào config repo

## 📝 Development Workflow

### 1. Tạo feature branch

```bash
git checkout -b feat/my-feature
```

### 2. Thực hiện thay đổi

Chỉ cần sửa code trong service folder tương ứng.

### 3. Push và tạo PR

```bash
git add .
git commit -m "feat(submission-service): add new feature"
git push origin feat/my-feature
```

Workflow sẽ tự động:
- Detect service thay đổi
- Build và test
- Push Docker image với tag feature

### 4. Merge vào main

Sau khi merge PR vào `main`:
- Version tự động tăng
- Tag được tạo: `<service>-v<version>`
- Docker image với version mới được push
- Config repo được update tự động

## 🎯 Services

### submission-service
Port: 8082  
Xử lý việc submit bài tập từ students.

### executor-service
Thực thi code trong môi trường sandbox an toàn.

### api-gateway
Port: 8080  
Gateway chính cho toàn bộ hệ thống.

### result-service
Quản lý và tính toán kết quả grading.

### course-service
Port: 8081  
Quản lý lớp học, sinh viên trong lớp, assignments, docker images, test plans và điểm số.

## 🧪 Testing Flow: Classes & Scores

Test trực tiếp course-service (`http://localhost:8081`) hoặc qua gateway.
Mọi request cần header `X-User-Id: <uuid>` — dùng **cùng một uuid** cho cả luồng
(đây là lecturer identity tạm thời cho tới khi tích hợp Keycloak; ownership check
sẽ trả 404 nếu dùng uuid khác với lúc tạo lớp).

### 1. Tạo lớp

```
POST /api/v1/classes
{ "name": "PTIT CNTT-K68", "semester": "20261" }
```

→ 201, lưu `id` từ response. Trùng name + semester (cùng owner) → 400 kèm message mô tả.

### 2. Import sinh viên từ CSV

```
POST /api/v1/classes/{classId}/students/import     (form-data, key `file` = file .csv)
```

File mẫu: `docs/samples/students-import.csv`.
Cột: `studentCode, studentName, email (tuỳ chọn), studentUserId UUID (tuỳ chọn)`.
→ `{imported, skipped}`. Import lại cùng file → tất cả `skipped`
(unique theo class + student code).

### 3. Khai báo thành phần điểm — bắt buộc trước khi nhập điểm

```
PUT /api/v1/classes/{classId}/score-components
[
  { "type": "ATTENDANCE", "weight": 0.10 },
  { "type": "EXERCISE",   "weight": 0.20 },
  { "type": "FINAL_EXAM", "weight": 0.70 }
]
```

Ràng buộc: type không trùng, `FINAL_EXAM` bắt buộc phải có và weight ≥ 0.40,
tổng weight = đúng 1.000. Vi phạm → 400.

### 4. Nhập điểm thủ công cho từng sinh viên

```
PUT /api/v1/classes/{classId}/students/B22DCCN001/scores
[
  { "componentType": "ATTENDANCE", "score": 8.5 },
  { "componentType": "FINAL_EXAM", "score": 7 }
]
```

Gửi `EXERCISE` → 400 (tự động chấm). Score ngoài khoảng 0–10 → 400 validation.

### 5. Xem điểm một sinh viên

```
GET /api/v1/classes/{classId}/students/B22DCCN001/scores
```

→ entries theo component kèm weight; `total = Σ score × weight` (thang 10),
kèm `letterGrade` (A+ → F) và `gpa` (thang 4). `total/letterGrade/gpa = null`
khi còn component nào thiếu điểm.
Quy tắc xếp loại: bất kỳ điểm thành phần nào = 0 → rớt ngay (F) dù tổng ≥ 4;
tổng < 4.0 → F. Bảng quy đổi: ≥9 A+/4.0 · ≥8.5 A/3.7 · ≥8 B+/3.5 · ≥7 B/3.0 ·
≥6.5 C+/2.5 · ≥5.5 C/2.0 · ≥5 D+/1.5 · ≥4 D/1.0 · <4 F/0.

### 6. Bảng điểm cả lớp

```
GET /api/v1/classes/{classId}/transcript
```

### EXERCISE tự động chấm — chuỗi điều kiện

`exercise` = trung bình `(score / max_score × 10)` trên các kết quả mới nhất của sinh
viên ở các assignment thuộc lớp, tính live qua result-service. Để exercise có giá trị
cần đủ cả 3 điều kiện:

1. `class_students.student_user_id` đã được set (cột thứ 4 trong CSV import hoặc update DB)
2. Class có assignments
3. result-service có results cho cặp (assignment, student) đó

Thiếu bất kỳ điều kiện nào → `exercise = null` và do đó `total = null`.

## 🔗 Related Repositories

- **Config Repo**: [web-grading-system-config](https://github.com/PTIT-DTL-Project/web-grading-system-config) - Helm charts và values
- **Deploy Repo**: [web-grading-system-deploy](https://github.com/PTIT-DTL-Project/web-grading-system-deploy) - ArgoCD orchestration

## 📚 Documentation

Chi tiết về architecture và deployment xem tại [deploy repo](https://github.com/PTIT-DTL-Project/web-grading-system-deploy).

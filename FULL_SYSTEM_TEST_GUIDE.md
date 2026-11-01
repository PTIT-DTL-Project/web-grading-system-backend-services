# Hướng Dẫn Test Full Luồng Hệ Thống (Từ A - Z)

Tài liệu này sẽ hướng dẫn bạn từ bước set up các dependencies (Database, Kafka, MinIO, Keycloak), khởi chạy các microservices, seed dữ liệu demo, và cuối cùng là dùng Postman để test luồng nộp bài (submit) qua API Gateway.

## 1. Chuẩn Bị Môi Trường

Hệ thống yêu cầu các thành phần sau:
- **PostgreSQL** (chứa 4 database: `assignment_db`, `submission_db`, `result_db`, `keycloak`)
- **Keycloak** (chạy ở port 8180, đã cấu hình sẵn realm và user)
- **Kafka & Zookeeper** (chạy ở port 9092)
- **MinIO** (Rustfs object storage, chạy ở port 9000)

Bạn **không cần cài đặt thủ công**, hệ thống đã có file `docker-compose.deps.yml` để chạy tất cả dependencies này.

### Bước 1.1: Chạy Dependencies
Mở terminal tại thư mục gốc của project (chứa file `docker-compose.deps.yml` vừa được tạo) và chạy:
```bash
docker-compose -f docker-compose.deps.yml up -d
```
Lệnh này sẽ tải các image cần thiết và khởi chạy:
1. `wgs-postgres-db` (Port: 5432, tự động tạo 4 DB qua script `init-dbs.sql`)
2. `wgs-zookeeper` (Port: 2181)
3. `wgs-kafka` (Port: 9092)
4. `wgs-minio` (Port: 9000, account: `minioadmin` / `minioadmin`)
5. `wgs-minio-setup` (Tự động tạo bucket `submission-files` rồi tự tắt)
6. `wgs-keycloak` (Port: 8180, tự động import data từ `ptit-wgs-realm.json`)

Bạn có thể kiểm tra xem các container đã chạy ổn định chưa bằng lệnh `docker ps`.

---

## 2. Khởi Chạy Các Microservices

Tất cả các service đã được cấu hình mặc định tương thích hoàn toàn với môi trường **Local**. Đồng thời mỗi thư mục service đã có sẵn file `.env` riêng (và file `.env` tổng ở thư mục gốc).

Bạn có thể chạy trực tiếp trên IDE (Run Application) hoặc qua Terminal mà không cần cấu hình thêm biến môi trường nào.

### Thứ tự & lệnh chạy:

#### 1. Course Service (Port: 8081)
- **Thư mục:** `course-service`
- **File cấu hình:** `course-service/.env`
- **Lệnh chạy:**
  ```bash
  cd course-service && mvn spring-boot:run
  ```

#### 2. Submission Service (Port: 8082)
- **Thư mục:** `submission-service`
- **File cấu hình:** `submission-service/.env`
- **Lệnh chạy:**
  ```bash
  cd submission-service && mvn spring-boot:run
  ```

#### 3. Executor Service (Port: 8083)
- **Thư mục:** `executor-service`
- **File cấu hình:** `executor-service/.env`
- **Lệnh chạy:**
  ```bash
  cd executor-service && mvn spring-boot:run
  ```

#### 4. Result Service (Port: 8084)
- **Thư mục:** `result-service`
- **File cấu hình:** `result-service/.env`
- **Lệnh chạy:**
  ```bash
  cd result-service && mvn spring-boot:run
  ```

#### 5. API Gateway (Port: 8080)
- **Thư mục:** `api-gateway`
- **File cấu hình:** `api-gateway/.env`
- **Lệnh chạy:**
  ```bash
  cd api-gateway && mvn spring-boot:run
  ```

*(Lưu ý: Bạn cũng có thể dùng `docker-compose up -d` với file `docker-compose.yml` gốc nhưng có thể sẽ bị lỗi do file gốc chưa tích hợp đầy đủ Kafka/MinIO và các biến môi trường trỏ chéo. Khuyến nghị chạy Dependencies qua Docker, chạy Microservices qua IDE).*

---

## 3. Chạy Script Seed Data (Dữ liệu Demo)

Khi `course-service` khởi động xong, nó sẽ dùng Flyway tạo ra các bảng trong database `assignment_db`.

Bây giờ bạn cần insert dữ liệu test vào DB. Mở terminal, chạy lệnh sau để inject file `seed_data_demo.sql` vào `wgs-postgres-db`:

```bash
docker exec -i wgs-postgres-db psql -U postgres -d assignment_db < seed_data_demo.sql
```
Dữ liệu được thêm bao gồm:
- Lớp học: Lập trình mạng
- Sinh viên: Student Test (khớp với Keycloak user ID)
- Bài tập: Bài tập 1: HTTP Server
- Test Plan: Test Cơ bản
- Test Step: Ping Test (Gọi API HTTP)

---

## 4. Test Luồng Nộp Bài Bằng Postman

### Request 1: Lấy JWT Token từ Keycloak (Vai trò Student)
- **Method**: `POST`
- **URL**: `http://localhost:8180/realms/ptit-wgs/protocol/openid-connect/token`
- **Headers**: `Content-Type: application/x-www-form-urlencoded`
- **Body** (x-www-form-urlencoded):
  - `client_id`: `wgs-postman`
  - `grant_type`: `password`
  - `username`: `student_test`
  - `password`: `<test-user-password>`

➔ **Kết quả:** Bạn sẽ nhận được `access_token`. Copy chuỗi token này.

---

### Request 2: Gọi thử API lấy chi tiết bài tập (Qua API Gateway)
- **Method**: `GET`
- **URL**: `http://localhost:8080/api/v1/student/assignments/55555555-5555-5555-5555-555555555555`
- **Headers**:
  - `Authorization`: `Bearer <DÁN_ACCESS_TOKEN_VÀO_ĐÂY>`

➔ **Kết quả (200 OK):** Gateway sẽ parse token, map header và route request sang `course-service`. Trả về thông tin "Bài tập 1: HTTP Server" (`id`: `55555555-5555-5555-5555-555555555555`).

*(Hoặc nếu muốn lấy danh sách bài tập của lớp `33333333-3333-3333-3333-333333333333`: `GET http://localhost:8080/api/v1/student/assignments?classId=33333333-3333-3333-3333-333333333333`)*

---

### Request 3: Xin Presigned URL để Upload bài giải (Qua API Gateway)
Để nộp bài, trước tiên sinh viên cần xin link MinIO để upload file Zip.
- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/submissions/presigned-url`
- **Headers**:
  - `Authorization`: `Bearer <DÁN_ACCESS_TOKEN_VÀO_ĐÂY>`
  - `Content-Type`: `application/json`
- **Body** (JSON):
```json
{
  "assignment_id": "55555555-5555-5555-5555-555555555555",
  "file_name": "bai1.zip"
}
```

➔ **Kết quả (200 OK):**
```json
{
  "upload_url": "http://localhost:9000/submission-files/... (link dài)",
  "rustfs_path": "55555555-5555-5555-5555-555555555555/22222222-2222-2222-2222-222222222222/bai1.zip",
  "expires_at": "..."
}
```
*(Hãy copy `upload_url` và `rustfs_path` để dùng ở bước tiếp theo).*

---

### Request 4: Upload file Zip lên MinIO (Trực tiếp)
- Tạo một file zip trống hoặc chứa code test đặt tên là `bai1.zip`.
- **Method**: `PUT`
- **URL**: `<DÁN_UPLOAD_URL_TỪ_BƯỚC_3>`
- **Headers**: *Không cần Auth header vì đây là presigned URL*
- **Body**: Chọn file `bai1.zip` của bạn (Trong Postman, chọn Binary -> Select File).

➔ **Kết quả:** `200 OK` (File đã lên MinIO).

---

### Request 5: Xác nhận Nộp Bài (Trigger luồng chấm điểm)
Đây là lúc hệ thống bắt đầu chạy pipeline: Gọi Submission Service ➔ Phát Kafka Event ➔ Executor Service nhận Event, tải file, chấm ➔ Gửi kết quả về Result Service.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/submissions`
- **Headers**:
  - `Authorization`: `Bearer <DÁN_ACCESS_TOKEN_VÀO_ĐÂY>`
  - `Content-Type`: `application/json`
- **Body** (JSON):
```json
{
  "assignment_id": "55555555-5555-5555-5555-555555555555",
  "rustfs_path": "<DÁN_RUSTFS_PATH_TỪ_BƯỚC_3>",
  "zip_file_name": "bai1.zip"
}
```

➔ **Kết quả (201 Created):**
```json
{
  "id": "uuid-cua-submission",
  "status": "PENDING",
  ...
}
```

### Theo Dõi Luồng Background:
Lúc này, hãy mở console/log của các service để quan sát luồng chạy tự động:
1. **`submission-service`**: Log báo đã lưu Submission và publish event `SubmissionCreatedEvent` lên Kafka topic `wgs-events`.
2. **`executor-service`**: Log báo đã nhận event từ Kafka, bắt đầu tải zip từ MinIO, giải nén và gọi test theo Test Plan.
3. **`result-service`**: Sau khi executor chấm xong, sẽ gửi API call (hoặc event) sang Result Service để lưu điểm số vào DB `result_db`.

---

### Request 6: Xem kết quả chấm điểm
- **Method**: `GET`
- **URL**: `http://localhost:8080/api/v1/results/assignment/55555555-5555-5555-5555-555555555555`
- **Headers**:
  - `Authorization`: `Bearer <DÁN_ACCESS_TOKEN_VÀO_ĐÂY>`

➔ **Kết quả:** Bạn sẽ nhận được list result tương ứng với bài nộp vừa thực hiện (kèm theo log pass/fail các step test).

🎉 **Chúc mừng! Bạn đã test thành công full luồng backend!**

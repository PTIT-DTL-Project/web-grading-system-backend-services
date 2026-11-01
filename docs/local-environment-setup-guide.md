# Hướng Dẫn Cài Đặt Môi Trường & Khởi Chạy Local (Từ A - Z)
## Dự án: Web Grading System Backend Services

Tài liệu này hướng dẫn chi tiết cách khởi tạo Database, Keycloak, cấu hình và chạy các service để test luồng từ Postman qua API Gateway tới các Microservices.

---

## 1. Kết Quả Kiểm Tra Môi Trường Trên Máy Bạn

| Thành phần | Trạng thái hiện tại trên máy | Ghi chú |
| :--- | :--- | :--- |
| **Java** | ✅ **Java 21.0.8 LTS** | Đã cài đặt tại `D:\Program Files\Java\jdk21` |
| **Maven** | ✅ **Maven 3.9.12** | Đã cài đặt tại `D:\Program Files\Apache\maven` |
| **PostgreSQL** | ✅ **PostgreSQL 18 (Đang chạy)** | Service `postgresql-x64-18` đang chạy trên port `5432` |
| **Database** | ⚠️ **Chưa tạo DB** | Cần tạo 4 database rỗng (Flyway sẽ tự động tạo bảng) |
| **Keycloak** | ⚠️ **Chưa chạy** | Cần chạy Keycloak ở port `8180` |

---

## 2. Bước 1: Khởi Tạo Database trên PostgreSQL

Hệ thống sử dụng các database độc lập cho từng microservice và Keycloak:
* `assignment_db`: Cho `course-service`
* `submission_db`: Cho `submission-service`
* `result_db`: Cho `result-service`
* `keycloak`: Cho `Keycloak IAM` (nếu dùng PostgreSQL)

> [!TIP]
> Bạn chỉ cần tạo **Database rỗng**, khi các Spring Boot service khởi động, **Flyway** sẽ tự động chạy các script migration để tạo toàn bộ bảng (`classes`, `assignments`, `submissions`,...).

### Cách thực hiện:

#### Cách 1: Dùng SQL Query trong pgAdmin 4 hoặc DBeaver
Mở công cụ quản lý DB (pgAdmin 4 có sẵn tại `C:\Program Files\PostgreSQL\18\pgAdmin 4`), đăng nhập với user `postgres` và chạy câu lệnh SQL sau:

```sql
CREATE DATABASE assignment_db;
CREATE DATABASE submission_db;
CREATE DATABASE result_db;
CREATE DATABASE keycloak;
```

#### Cách 2: Dùng PowerShell / CMD với `psql`
Mở PowerShell và chạy lệnh sau (nhập mật khẩu của user `postgres` khi được hỏi):

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -c "CREATE DATABASE assignment_db;"
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -c "CREATE DATABASE submission_db;"
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -c "CREATE DATABASE result_db;"
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -c "CREATE DATABASE keycloak;"
```

---

## 3. Bước 2: Khởi Chạy Keycloak (Port 8180)

Chọn 1 trong 2 cách sau:

### Lựa chọn A: Dùng Keycloak Standalone (Không cần Docker - Rất nhẹ)
1. Tải bản **Keycloak 26.x (Zip)** từ trang chủ: [Keycloak Downloads](https://www.keycloak.org/downloads)
2. Giải nén vào một thư mục (ví dụ `D:\keycloak-26.0.0`).
3. Mở PowerShell tại thư mục vừa giải nén và chạy lệnh ở chế độ Dev trên port **8180**:
   ```powershell
   cd D:\keycloak-26.0.0
   $env:KEYCLOAK_ADMIN="admin"
   $env:KEYCLOAK_ADMIN_PASSWORD="admin"
   .\bin\kc.bat start-dev --http-port=8180
   ```
4. Truy cập giao diện quản trị: **`http://localhost:8180`** (Tài khoản: `admin` / `admin`).

---

### Lựa chọn B: Dùng Docker Desktop
Nếu máy bạn có cài Docker Desktop, chỉ cần chạy 2 container Keycloak và Keycloak-DB:
```powershell
docker compose up -d keycloak-db keycloak
```

---

## 4. Bước 3: Cấu Hình Keycloak (Realm, Client, Roles, User)

Truy cập `http://localhost:8180/admin` và thao tác:

### 1. Tạo Realm
* Ở menu góc trên bên trái (đang chọn *master*), bấm **Create Realm**.
* Realm name: **`ptit-wgs`** ➔ Bấm **Create**.

### 2. Tạo Realm Roles
* Ở menu bên trái, vào **Realm Roles** ➔ Bấm **Create Role**:
  * Tạo role `ROLE_STUDENT`
  * Tạo role `ROLE_LECTURER`
  * Tạo role `ROLE_ADMIN`

### 3. Tạo Client cho Postman / Frontend
* Ở menu bên trái, vào **Clients** ➔ Bấm **Create client**:
  * **Client type**: `OpenID Connect`
  * **Client ID**: `wgs-postman`
  * Bấm **Next**.
  * Bật mục **Direct access grants** (cho phép login bằng username/password từ Postman).
  * **Client authentication**: Chọn `Off` (Public Client).
  * Bấm **Save**.

### 4. Tạo User Test
* Vào **Users** ➔ Bấm **Add user**:
  * **Username**: `student_test`
  * **Email**: `lecturer@example.test`
  * **Email verified**: `Yes`
  * Bấm **Create**.
* Sang tab **Credentials** của user:
  * Bấm **Set password** ➔ Nhập `<test-user-password>`
  * Tắt ô **Temporary** (để không bị bắt đổi mật khẩu lần đầu) ➔ Bấm **Save**.
* Sang tab **Role mapping**:
  * Bấm **Assign role** ➔ Chọn **`ROLE_STUDENT`** ➔ Bấm **Assign**.

---

## 5. Bước 4: Khởi Chạy API Gateway & Course Service

### 1. Khởi chạy `course-service` (Port 8081)
* **Trong IntelliJ IDEA / Eclipse:**
  * Mở class `CourseServiceApplication.java` và bấm **Run**.
* **Hoặc bằng Terminal:**
  ```powershell
  cd d:\Project\web-grading-system-backend-services\course-service
  mvn spring-boot:run
  ```
  *(Khi chạy, Flyway sẽ tự động kết nối vào `assignment_db` và tạo các bảng)*.

### 2. Khởi chạy `api-gateway` (Port 8080)
Khi chạy local, cần truyền biến cấu hình để Gateway biết trỏ đến Keycloak port 8180 và Course Service port 8081:
* **Trong IntelliJ IDEA:**
  * Edit Configurations ➔ Thêm Environment Variables:
    * `KEYCLOAK_ISSUER_URI=http://localhost:8180/realms/ptit-wgs`
    * `COURSE_SERVICE_URI=http://localhost:8081`
* **Hoặc bằng Terminal:**
  ```powershell
  cd d:\Project\web-grading-system-backend-services\api-gateway
  $env:KEYCLOAK_ISSUER_URI="http://localhost:8180/realms/ptit-wgs"
  $env:COURSE_SERVICE_URI="http://localhost:8081"
  mvn spring-boot:run
  ```

---

## 6. Bước 5: Test Chuẩn Luồng Bằng Postman

### Request 1: Lấy Token từ Keycloak
* **Method**: `POST`
* **URL**: `http://localhost:8180/realms/ptit-wgs/protocol/openid-connect/token`
* **Headers**: `Content-Type: application/x-www-form-urlencoded`
* **Body** (`x-www-form-urlencoded`):
  * `client_id` : `wgs-postman`
  * `grant_type` : `password`
  * `username` : `student_test`
  * `password` : `<test-user-password>`

Response trả về:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIs...",
  "expires_in": 300,
  "token_type": "Bearer"
}
```
👉 *Copy giá trị chuỗi `access_token`.*

---

### Request 2: Gọi API qua API Gateway KHÔNG có Token
* **Method**: `GET`
* **URL**: `http://localhost:8080/api/v1/classes`
* **Auth**: `No Auth`

Kết quả: **`401 Unauthorized`** (Gateway chặn ngay lập tức).

---

### Request 3: Gọi API qua API Gateway CÓ Token hợp lệ
* **Method**: `GET`
* **URL**: `http://localhost:8080/api/v1/classes`
* **Headers**:
  * `Authorization`: `Bearer <dán_access_token_vào_đây>`

Kết quả: **`200 OK`**.
Gateway tự động bóc tách JWT từ Keycloak, gắn các header `X-User-Id`, `X-User-Roles`, `X-User-Email` và chuyển tiếp đến `course-service`.

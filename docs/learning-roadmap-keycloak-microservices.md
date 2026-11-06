# Lộ trình Học tập & Nắm vững Kiến thức
## Từ Java Backend Base đến Tích hợp Keycloak + API Gateway trong Microservices

---

## 📌 Lời khuyên ban đầu

Bạn không cần phải học tất cả mọi thứ về Microservices hay Keycloak mới làm được. Với nền tảng Java Backend sẵn có, bạn chỉ cần nắm đúng **5 khối kiến thức cốt lõi** dưới đây theo thứ tự từ lý thuyết đến thực hành.

---

## 🎯 Khối 1: Hiểu sự khác biệt về Auth (Monolith vs Microservices)

### 1. Web Monolith truyền thống (Kiến trúc cũ):
* Đăng nhập -> Server lưu `Session` (JSESSIONID) vào bộ nhớ / Redis, hoặc trả JWT về -> Client gửi lại Token -> Filter kiểm tra Database.
* Tất cả nằm trong **1 ứng dụng duy nhất**.

### 2. Kiến trúc Microservices (Dự án hiện tại):
* Hệ thống tách thành nhiều dịch vụ nhỏ (`course-service`, `submission-service`, `executor-service`...).
* **Vấn đề**: Không thể bắt từng service tự query database để kiểm tra mật khẩu/tài khoản (rất phân tán và lặp lại code).
* **Giải pháp (API Gateway Pattern)**:
  * **API Gateway** đứng ở cổng ngoài cùng tiếp nhận request.
  * **Keycloak (IAM)** đứng riêng làm nhiệm vụ Đăng nhập / Cấp Token.
  * API Gateway kiểm tra Token từ Keycloak -> Trích xuất thông tin người dùng (`user_id`, `roles`) -> Gửi thông tin đó xuống các microservice bên dưới qua **HTTP Header** (`X-User-Id`, `X-User-Roles`).

---

## 🔐 Khối 2: Nắm vững Chuẩn Auth (JWT + OAuth2 + OIDC)

Bạn cần hiểu 3 khái niệm cơ bản này để không bị ngợp khi đọc log hay cấu hình code:

### 1. Cấu trúc của JWT (JSON Web Token)
JWT gồm 3 phần phân cách bởi dấu chấm `.`: `Header.Payload.Signature`
* **Header**: Chứa thuật toán mã hóa (VD: RS256).
* **Payload**: Chứa thông tin người dùng (gọi là **Claims**) như `sub` (User ID), `preferred_username`, `email`, `realm_access.roles`.
* **Signature**: Chữ ký số dùng để Gateway kiểm tra Token có bị giả mạo hay không.
👉 *Thực hành*: Đăng nhập thử và copy token vào trang [jwt.io](https://jwt.io) để xem ruột bên trong JWT.

### 2. Phân biệt OAuth 2.0 và OpenID Connect (OIDC)
* **OAuth 2.0**: Chuẩn dùng để **Ủy quyền (Authorization)** - cho phép app này truy cập tài nguyên thay mặt user.
* **OpenID Connect (OIDC)**: Lớp mở rộng nằm trên OAuth 2.0 dùng để **Xác thực (Authentication)** - trả về thông tin ai đang đăng nhập (ID Token).
* Keycloak triển khai chuẩn OIDC này.

### 3. Luồng đăng nhập PKCE (Authorization Code Flow with PKCE)
* Đây là chuẩn đăng nhập an toàn nhất cho Web Frontend (React/Vue/Next.js):
  1. User bấm Đăng nhập -> Chuyển hướng sang giao diện Keycloak.
  2. User nhập tài khoản/mật khẩu tại Keycloak.
  3. Keycloak trả về **Authorization Code** cho Frontend.
  4. Frontend đổi Code lấy **Access Token (JWT)**.
  5. Frontend gửi Access Token này kèm mỗi API request lên API Gateway.

---

## 🔑 Khối 3: Học làm chủ Keycloak (Thực hành trên giao diện)

Keycloak là một phần mềm độc lập. Ban đầu bạn **chưa cần viết dòng code Java nào**, hãy cài và bấm thử trên Admin Console của Keycloak.

### Khái niệm cần nhớ trên Keycloak Admin Console:
1. **Realm**: Một "không gian" quản lý người dùng độc lập (Hãy tạo 1 Realm tên `ptit-wgs`).
2. **Client**: Ứng dụng kết nối tới Keycloak (VD: `wgs-frontend` cho React, `wgs-gateway` cho Spring Cloud Gateway).
3. **Users**: Quản lý danh sách tài khoản (Tạo thử 1 user sinh viên, 1 user giảng viên).
4. **Roles**: Danh sách quyền (Tạo `ROLE_STUDENT`, `ROLE_LECTURER`, `ROLE_ADMIN`).
5. **Client Mappers / Protocol Mappers**: Cấu hình để Keycloak đính kèm các thông tin tùy biến (như `roles`, `email`) vào JWT Payload.

👉 *Cách cài nhanh nhất*: Dùng Docker chạy 1 câu lệnh để có ngay Keycloak trên `localhost:8080`.

---

## ⚡ Khối 4: Spring Cloud Gateway & Spring WebFlux

Microservice `api-gateway` của bạn dùng **Spring Cloud Gateway**. Điểm khác biệt lớn nhất so với Spring MVC thông thường:

### 1. Spring MVC (Truyền thống) vs Spring WebFlux (Reactive)
* **Spring MVC**: Blocking I/O (Mỗi request dùng 1 Thread, dùng `HttpServletRequest`, `FilterChain`).
* **Spring WebFlux**: Non-blocking Reactive (Dùng `ServerWebExchange`, `Mono<T>`, `Flux<T>`).

### 2. Các thành phần chính trong Spring Cloud Gateway:
* **Routes**: Định hướng request (VD: Request bắt đầu `/api/v1/classes/**` sẽ chuyển về `course-service`).
* **GlobalFilter**: Đoạn code chạy qua **mọi request**. Bạn sẽ viết code xác thực & chèn Header `X-User-Id` tại đây!

### 3. Thư viện `spring-boot-starter-oauth2-resource-server`
* Đây là thư viện của Spring Security giúp Spring Cloud Gateway tự động tải Public Key từ Keycloak về để kiểm tra Token JWT gửi lên có hợp lệ hay không.

---

## 🚀 Khối 5: Lộ trình Thực hành Từng Bước (Step-by-Step Action Plan)

Dành ra **2 - 3 ngày** học và làm theo thứ tự sau:

```
[Bước 1: Chạy Docker Keycloak]
       │
       ▼
[Bước 2: Cấu hình Realm, Client, User, Role trên Keycloak UI]
       │
       ▼
[Bước 3: Dùng Postman lấy Token & Decode trên JWT.io]
       │
       ▼
[Bước 4: Thử nghiệm Spring Cloud Gateway nhận JWT Token]
       │
       ▼
[Bước 5: Viết Filter chèn Header X-User-Id chuyển xuống Downstream Service]
```

### Chi tiết từng bước:

#### 🔹 Bước 1: Dùng Docker chạy Keycloak Local
Chạy lệnh terminal sau để bật Keycloak:
```bash
docker run -p 8080:8080 -e KEYCLOAK_ADMIN=admin -e KEYCLOAK_ADMIN_PASSWORD=admin quay.io/keycloak/keycloak:24.0.0 start-dev
```
* Truy cập `http://localhost:8080/admin` với user: `admin` / pass: `admin`.

#### 🔹 Bước 2: Tập thao tác trên Keycloak Console
1. Tạo Realm tên `ptit-wgs`.
2. Tạo Role `ROLE_LECTURER` và `ROLE_STUDENT`.
3. Tạo 1 User tên `lecturer1`, đặt password và gán role `ROLE_LECTURER`.
4. Tạo Client tên `postman-test` (Bật Direct Access Grants để test lấy token qua Postman).

#### 🔹 Bước 3: Lấy Token và Đọc JWT
1. Mở Postman, gửi request `POST http://localhost:8080/realms/ptit-wgs/protocol/openid-connect/token` với body dạng `x-www-form-urlencoded`:
   * `client_id`: `postman-test`
   * `grant_type`: `password`
   * `username`: `lecturer1`
   * `password`: `<mật khẩu vừa đặt>`
2. Coppy chuỗi `access_token` dán vào trang [jwt.io](https://jwt.io) để xem thông tin payload.

#### 🔹 Bước 4 & 5: Đọc và triển khai Code trong dự án
* Mở file thiết kế tôi đã viết cho bạn tại [keycloak-integration-design.md](file:///d:/Project/web-grading-system-backend-services/docs/keycloak-integration-design.md).
* Bắt đầu sửa code ở service `api-gateway` ([pom.xml](file:///d:/Project/web-grading-system-backend-services/api-gateway/pom.xml)) theo đúng hướng dẫn mục 4.2 trong tài liệu thiết kế.

---

## 📚 Từ khóa tìm kiếm (Cheatsheet cho bạn tra Google / Youtube)

khi bí kiến thức hoặc muốn xem video giải thích, hãy gõ chính xác các từ khóa sau:
1. `OAuth2 and OpenID Connect explained` (Xem video hình vẽ trực quan).
2. `Keycloak beginner tutorial docker` (Xem cách bấm trên giao diện Keycloak).
3. `Spring Cloud Gateway Keycloak OAuth2 Resource Server` (Xem hướng dẫn tích hợp Java).
4. `Spring Cloud Gateway GlobalFilter add request header` (Xem cách viết filter chèn Header).

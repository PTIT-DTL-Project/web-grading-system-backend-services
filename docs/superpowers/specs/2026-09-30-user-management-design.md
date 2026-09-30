# User Management & Auth Flow — Design Spec

> **Hệ thống**: Web Grading System Backend (PTIT)
> **Ngày**: 2026-09-30
> **Phạm vi**: user-service mới, auth flow, Keycloak integration, admin CRUD + import

---

## 1. Tổng quan

Tạo microservice `user-service` mới để quản lý toàn bộ vòng đời người dùng (Admin, Giảng viên, Sinh viên). Keycloak là **source of truth** duy nhất cho auth và user data. `user-service` wrap Keycloak Admin REST API, không có DB riêng cho user.

---

## 2. Kiến trúc

```
[Client / Frontend]
       │
       │  JWT Bearer Token (mọi request sau login)
       ▼
[API Gateway :8080]  — Spring Cloud Gateway
       │  validate JWT với Keycloak JWKS
       │  inject: X-User-Id, X-User-Email, X-User-Roles, X-Gateway-Secret
       │
       ├── /api/v1/auth/**          → user-service :8085  (PUBLIC — no JWT needed)
       ├── /api/v1/users/**         → user-service :8085  (Authenticated)
       ├── /api/v1/admin/users/**   → user-service :8085  (ROLE_ADMIN only)
       ├── /api/v1/courses/**       → course-service :8081
       ├── /api/v1/submissions/**   → submission-service :8082
       └── /api/v1/results/**       → result-service :8084

[user-service :8085]
       │  Keycloak Admin REST API (service account credentials)
       ▼
[Keycloak :8180] — ptit-wgs realm
       - source of truth: auth + user profile + custom attributes
```

### Tích hợp với security hiện tại

`user-service` theo đúng pattern của các service hiện có:
- Nhận `X-User-*` headers từ gateway (không validate JWT trực tiếp)
- Dùng `HeaderAuthenticationFilter` + `UserPrincipal` cùng pattern như `course-service`
- Endpoint `/api/v1/auth/**` và `/api/v1/internal/**` được permit all tại service

---

## 3. Keycloak User Attributes

### 3.1 Attributes chung (LECTURER & STUDENT)

| Attribute Key | Kiểu | Bắt buộc | Ví dụ |
|---------------|------|----------|-------|
| `phone_number` | String | Không | `0901234567` |
| `gender` | String | Không | `MALE` / `FEMALE` / `OTHER` |
| `date_of_birth` | String (ISO) | Không | `1999-01-15` |
| `avatar_url` | String | Không | URL ảnh |
| `status` | String | Có | `ACTIVE` / `INACTIVE` |

### 3.2 Attributes STUDENT

| Attribute Key | Kiểu | Bắt buộc | Ví dụ |
|---------------|------|----------|-------|
| `student_code` | String | Có | `B22DCCN001` |
| `department` | String | Không | `CNTT` |
| `batch` | String | Không | `D22` |
| `program` | String | Không | `Kỹ thuật phần mềm` |
| `class_code` | String | Không | `D22CQCN01-N` |

### 3.3 Attributes LECTURER

| Attribute Key | Kiểu | Bắt buộc | Ví dụ |
|---------------|------|----------|-------|
| `staff_code` | String | Có | `GV001` |
| `department` | String | Không | `CNTT` |
| `title` | String | Không | `ThS` / `TS` / `GS` |

### 3.4 Keycloak Standard Fields (đã có)

- `firstName`, `lastName`, `email`, `username` — Keycloak native
- `enabled` — true/false (thay cho soft delete)
- `realmRoles` — `ROLE_ADMIN`, `ROLE_LECTURER`, `ROLE_STUDENT`
- `requiredActions` — `["UPDATE_PASSWORD"]` khi cần force đổi mật khẩu

---

## 4. API Endpoints

### 4.1 Auth (PUBLIC — gateway permitAll)

```
POST /api/v1/auth/login
  Body: { username: string, password: string }
  Response 200: { accessToken, refreshToken, tokenType, expiresIn, requiresPasswordChange? }
  Note: detect required_action UPDATE_PASSWORD bằng cách gọi Admin API sau khi login thành công

POST /api/v1/auth/refresh
  Body: { refreshToken: string }
  Response 200: { accessToken, refreshToken, expiresIn }

POST /api/v1/auth/logout
  Body: { refreshToken: string }
  Response 204

POST /api/v1/auth/change-password
  Header: Authorization: Bearer <access_token>
  Body: { currentPassword: string, newPassword: string }
  Response 204
  Note: gọi Keycloak Admin API xóa required_action UPDATE_PASSWORD sau khi đổi thành công
```

### 4.2 User Profile (Authenticated)

```
GET /api/v1/users/me
  Response 200: UserProfileResponse

PUT /api/v1/users/me
  Body: UpdateProfileRequest (phone_number, gender, date_of_birth, avatar_url, ...)
  Response 200: UserProfileResponse
```

### 4.3 Admin — User Management (ROLE_ADMIN)

```
GET /api/v1/admin/users
  Query: role=[STUDENT|LECTURER|ADMIN], search=<string>, page=0, size=20
  Response 200: Page<UserSummaryResponse>

POST /api/v1/admin/users
  Body: CreateUserRequest
  Response 201: UserProfileResponse

GET /api/v1/admin/users/{id}
  Response 200: UserProfileResponse

PUT /api/v1/admin/users/{id}
  Body: UpdateUserRequest
  Response 200: UserProfileResponse

DELETE /api/v1/admin/users/{id}
  Note: soft delete — set Keycloak user enabled=false + attribute status=INACTIVE
  Response 204

POST /api/v1/admin/users/{id}/reset-password
  Body: { newPassword: string, forceChange: boolean }
  Response 204

POST /api/v1/admin/users/import
  Content-Type: multipart/form-data
  Fields:
    - file: CSV hoặc XLSX
    - passwordMode: "STUDENT_CODE" | "CUSTOM"
    - defaultPassword: (chỉ khi passwordMode=CUSTOM)
    - forcePasswordChange: true | false
  Response 200: ImportResultResponse { total, success, failed, errors: [{row, reason}] }
  Note: xử lý đồng bộ, row nào lỗi thì skip và ghi lỗi, row khác vẫn import
```

---

## 5. CSV/Excel Import Format

### STUDENT import (CSV header bắt buộc):

```csv
username,email,firstName,lastName,studentCode,department,batch,program,classCode,phone,gender
b22dccn001,b22dccn001@ptit.edu.vn,An,Nguyen,B22DCCN001,CNTT,D22,Kỹ thuật phần mềm,D22CQCN01-N,0901234567,MALE
b22dccn002,b22dccn002@ptit.edu.vn,Bich,Tran,B22DCCN002,CNTT,D22,Kỹ thuật phần mềm,D22CQCN01-N,,FEMALE
```

### LECTURER import (CSV header bắt buộc):

```csv
username,email,firstName,lastName,staffCode,department,title,phone,gender
gv001,gv001@ptit.edu.vn,Van Tung,Tran,GV001,CNTT,ThS,0912345678,MALE
```

### Password logic:
- `passwordMode=STUDENT_CODE` → password = giá trị cột `studentCode`; tự động `forcePasswordChange=true`
- `passwordMode=CUSTOM` → password = `defaultPassword` param; `forcePasswordChange` theo param
- Validation: nếu `passwordMode=STUDENT_CODE` và `studentCode` rỗng → row đó fail

---

## 6. First-Login Flow

```
1. User POST /api/v1/auth/login { username, password }
2. user-service gọi Keycloak: POST /realms/ptit-wgs/protocol/openid-connect/token
3a. Nếu Keycloak trả 200 (login thành công):
    - Parse access_token, lưu userId = subject
    - Gọi Keycloak Admin API: GET /admin/realms/ptit-wgs/users/{userId}
    - Check response.requiredActions
    - Nếu KHÔNG có UPDATE_PASSWORD → trả full token response bình thường
    - Nếu CÓ UPDATE_PASSWORD → trả { accessToken, refreshToken, requiresPasswordChange: true }
3b. Nếu Keycloak trả 401 → trả 401 cho client

4. Client phát hiện requiresPasswordChange=true → redirect đến change-password page
5. User POST /api/v1/auth/change-password { currentPassword, newPassword }
   Header: Authorization: Bearer <access_token từ bước 3>
6. user-service:
   a. Verify current password (gọi Keycloak token endpoint thử lại với currentPassword)
   b. Gọi Admin API: PUT /admin/realms/ptit-wgs/users/{userId}/reset-password với {type:"password", value:newPassword, temporary:false}
   c. Gọi Admin API: PUT /admin/realms/ptit-wgs/users/{userId} với {requiredActions:[]} để xóa UPDATE_PASSWORD action
7. Trả 204 → client login lại bình thường → full access
```

---

## 7. Roles & Permissions

| Action | ROLE_ADMIN | ROLE_LECTURER | ROLE_STUDENT |
|--------|:----------:|:-------------:|:------------:|
| Login / Refresh / Logout | ✅ | ✅ | ✅ |
| Change own password | ✅ | ✅ | ✅ |
| View own profile | ✅ | ✅ | ✅ |
| Update own profile | ✅ | ✅ | ✅ |
| List all users | ✅ | ❌ | ❌ |
| Create user (any role) | ✅ | ❌ | ❌ |
| Edit any user | ✅ | ❌ | ❌ |
| Disable/enable user | ✅ | ❌ | ❌ |
| Reset any user's password | ✅ | ❌ | ❌ |
| Import CSV/Excel | ✅ | ❌ | ❌ |

---

## 8. Gateway Configuration Changes

### 8.1 `SecurityConfig.java` — thêm public paths:

```java
private static final String[] PUBLIC_PATHS = {
    "/actuator/health",
    "/actuator/info",
    "/actuator/prometheus",
    "/api/v1/auth/**"          // NEW — auth endpoints không cần JWT
};
```

### 8.2 Route rules cần thêm vào `application.yaml` gateway:

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          uri: http://user-service:8085
          predicates:
            - Path=/api/v1/auth/**, /api/v1/users/**, /api/v1/admin/users/**
```

### 8.3 `GatewayTrustProperties` — allowedRoles:

Đảm bảo `ROLE_ADMIN` có trong `allowedRoles` để được forward trong `X-User-Roles` header.

---

## 9. Keycloak Service Account

`user-service` cần một Keycloak client (service account) để gọi Admin REST API:

```
Client ID: wgs-user-service
Client Secret: <generated>
Grant Type: client_credentials
Service Account Roles:
  - realm-management > manage-users
  - realm-management > view-users
  - realm-management > manage-realm (để xóa required_actions)
```

Credentials lưu trong `.env`:
```env
KEYCLOAK_ADMIN_CLIENT_ID=wgs-user-service
KEYCLOAK_ADMIN_CLIENT_SECRET=<secret>
KEYCLOAK_SERVER_URL=http://keycloak:8080
KEYCLOAK_REALM=ptit-wgs
```

---

## 10. Seed Data Changes (`ptit-wgs-realm.json`)

Cần bổ sung:

1. Client `wgs-user-service` với service account enabled và roles
2. Admin user seed:
```json
{
  "username": "admin",
  "email": "admin@ptit.edu.vn",
  "firstName": "System",
  "lastName": "Admin",
  "enabled": true,
  "emailVerified": true,
  "realmRoles": ["ROLE_ADMIN"],
  "credentials": [{ "type": "password", "value": "Admin@123", "temporary": true }],
  "attributes": { "status": ["ACTIVE"] }
}
```
3. Update existing seed users (lecturer_test, student_test) với custom attributes mới

---

## 11. Cấu trúc `user-service`

```
user-service/
├── src/main/java/vn/edu/ptit/.../user_service/
│   ├── UserServiceApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java            # header-based auth, permit /auth/**
│   │   ├── KeycloakAdminConfig.java       # Keycloak Admin Client bean
│   │   └── OpenApiConfig.java
│   ├── controller/
│   │   ├── AuthController.java            # /api/v1/auth/**
│   │   ├── UserProfileController.java     # /api/v1/users/me
│   │   └── AdminUserController.java       # /api/v1/admin/users/**
│   ├── dto/
│   │   ├── request/
│   │   │   ├── LoginRequest.java
│   │   │   ├── RefreshTokenRequest.java
│   │   │   ├── ChangePasswordRequest.java
│   │   │   ├── CreateUserRequest.java
│   │   │   ├── UpdateUserRequest.java
│   │   │   └── ResetPasswordRequest.java
│   │   └── response/
│   │       ├── LoginResponse.java
│   │       ├── TokenResponse.java
│   │       ├── UserProfileResponse.java
│   │       ├── UserSummaryResponse.java
│   │       └── ImportResultResponse.java
│   ├── security/
│   │   ├── HeaderAuthenticationFilter.java
│   │   ├── UserPrincipal.java
│   │   └── SecurityUtils.java
│   ├── service/
│   │   ├── AuthService.java               # login, refresh, logout, change-password
│   │   ├── UserAdminService.java          # CRUD via Keycloak Admin API
│   │   └── ImportService.java             # parse CSV/Excel, batch create
│   └── exception/
│       └── GlobalExceptionHandler.java
├── pom.xml
└── Dockerfile
```

---

## 12. Dependencies chính

```xml
<!-- Keycloak Admin Client -->
<dependency>
    <groupId>org.keycloak</groupId>
    <artifactId>keycloak-admin-client</artifactId>
    <version>26.0.0</version>
</dependency>

<!-- Excel parsing -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
</dependency>

<!-- CSV parsing -->
<dependency>
    <groupId>com.opencsv</groupId>
    <artifactId>opencsv</artifactId>
    <version>5.9</version>
</dependency>
```

---

## 13. Out of Scope (có thể thêm sau)

- Email notification khi admin tạo account
- Async import với job polling (đồng bộ đủ cho <1000 rows)
- OAuth2 social login (Google, PTIT SSO)
- Admin approval workflow cho self-registration
- Keycloak Protocol Mapper để inject custom attributes vào JWT claims

# AI Student Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Xây dựng tính năng AI Trợ giảng Thông minh cho Sinh viên — bao gồm backend microservice `ai-assistant-service` (Spring Boot + Spring AI), mở rộng telemetry thu thập N+1 query / stack trace / memory trong `executor-service` & `result-service`, và giao diện Frontend React (nút phân tích 1 lần duy nhất + panel chẩn đoán 4 trụ cột).

**Architecture:** Tạo microservice mới `ai-assistant-service` (cổng 8086, DB `ai_db`, Spring AI ChatModel) giao tiếp nội bộ với `result-service` và `course-service` qua OpenFeign. API Gateway route `/api/v1/ai/**` với JWT xác thực & header enrichment. Frontend React tích hợp 1 nút phân tích toàn bài, tự động kiểm tra cache khi mount và render `AiAnalysisPanel` dạng Card với 4 trụ cột phân tích.

**Tech Stack:** Spring Boot 4.1.0 (Java 21), Spring AI / ChatModel abstraction, PostgreSQL, Flyway, OpenFeign, React 18, TypeScript, Ant Design 5, Vite, TanStack Query (React Query).

## Global Constraints

- **Scope:** Chỉ triển khai cho Sinh viên; tính năng Copilot cho Giảng viên tạm hoãn (Phase 2).
- **Single-Click Constraint:** Nút AI chỉ bấm được 1 lần duy nhất per submission; kết quả lưu cache vĩnh viễn trong `ai_explanations`. Lần sau vào lại trang sẽ tải từ cache và disable nút.
- **Port Allocation:** `api-gateway` (8080), `course-service` (8081), `submission-service` (8082), `result-service` (8084), `user-service` (8084), `executor-service` (8085), `ai-assistant-service` (8086).
- **Pedagogical Rule:** AI không được viết code giải sẵn; phải đưa ra chẩn đoán nguyên nhân cốt lõi, vị trí nghi vấn và gợi ý hướng xử lý (Hints) cùng từ khóa tự học (Learning Keywords).
- **Redaction Rule:** Không để lộ dữ liệu nhạy cảm hoặc input/payload của hidden test cases trong prompt gửi sang LLM.

---

### Task 1: Database Setup & Flyway Schema Migrations

**Files:**
- Modify: `init-dbs.sql`
- Create: `result-service/src/main/resources/db/migration/V8__2026-10-15__add_telemetry_to_step_results.sql`
- Create: `executor-service/src/main/resources/db/migration/V9__2026-10-15__add_telemetry_fields.sql`
- Create: `ai-assistant-service/src/main/resources/db/migration/V1__2026-10-15__init_ai_db.sql`

**Interfaces:**
- Consumes: Existing DB schemas in `result_db` and `executor_db`.
- Produces: Database `ai_db`, table `ai_explanations`, telemetry columns in `step_results`, `grading_step_results`, and `grading_jobs`.

- [ ] **Step 1: Add `ai_db` creation to `init-dbs.sql`**

Append `CREATE DATABASE ai_db;` to `web-grading-system-backend-services/init-dbs.sql`:
```sql
CREATE DATABASE assignment_db;
CREATE DATABASE submission_db;
CREATE DATABASE result_db;
CREATE DATABASE executor_db;
CREATE DATABASE keycloak;
CREATE DATABASE ai_db;
```

- [ ] **Step 2: Create Flyway migration for `result-service`**

File: `result-service/src/main/resources/db/migration/V8__2026-10-15__add_telemetry_to_step_results.sql`
```sql
ALTER TABLE step_results
ADD COLUMN IF NOT EXISTS db_query_count INTEGER,
ADD COLUMN IF NOT EXISTS db_query_logs TEXT,
ADD COLUMN IF NOT EXISTS stack_trace TEXT,
ADD COLUMN IF NOT EXISTS peak_memory_mb INTEGER;
```

- [ ] **Step 3: Create Flyway migration for `executor-service`**

File: `executor-service/src/main/resources/db/migration/V9__2026-10-15__add_telemetry_fields.sql`
```sql
ALTER TABLE grading_step_results
ADD COLUMN IF NOT EXISTS db_query_count INTEGER,
ADD COLUMN IF NOT EXISTS db_query_logs TEXT,
ADD COLUMN IF NOT EXISTS stack_trace TEXT;

ALTER TABLE grading_jobs
ADD COLUMN IF NOT EXISTS app_container_logs TEXT,
ADD COLUMN IF NOT EXISTS peak_memory_mb INTEGER,
ADD COLUMN IF NOT EXISTS peak_cpu_percent NUMERIC(5,2);
```

- [ ] **Step 4: Create initial Flyway migration for `ai-assistant-service`**

File: `ai-assistant-service/src/main/resources/db/migration/V1__2026-10-15__init_ai_db.sql`
```sql
CREATE TABLE IF NOT EXISTS ai_explanations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id UUID NOT NULL,
    student_id UUID NOT NULL,
    category VARCHAR(30) NOT NULL DEFAULT 'FULL_ANALYSIS',
    diagnosis TEXT NOT NULL,
    suspected_location TEXT,
    actionable_hints JSONB NOT NULL,
    learning_keywords JSONB,
    telemetry_audit JSONB,
    overall_summary TEXT,
    model_name VARCHAR(50),
    prompt_tokens INTEGER,
    completion_tokens INTEGER,
    feedback_rating SMALLINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    deleted_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_ai_explain_lookup ON ai_explanations(submission_id, category);
CREATE INDEX IF NOT EXISTS idx_ai_explain_student ON ai_explanations(student_id);
```

- [ ] **Step 5: Verify SQL scripts format and commit**

Run: Verify SQL syntax with dry-run or linting.
Commit:
```bash
git add init-dbs.sql result-service/src/main/resources/db/migration/V8* executor-service/src/main/resources/db/migration/V9* ai-assistant-service/src/main/resources/db/migration/V1*
git commit -m "feat(ai): add database migrations for ai_db and telemetry fields"
```

---

### Task 2: Telemetry Data Model in `result-service` and `executor-service`

**Files:**
- Modify: `result-service/src/main/java/vn/edu/ptit/web_grading_system/result_service/entity/StepResult.java`
- Modify: `result-service/src/main/java/vn/edu/ptit/web_grading_system/result_service/dto/response/StepResultResponse.java`
- Modify: `result-service/src/main/java/vn/edu/ptit/web_grading_system/result_service/dto/request/CreateResultRequest.java`
- Modify: `result-service/src/main/java/vn/edu/ptit/web_grading_system/result_service/service/ResultService.java`
- Modify: `executor-service/src/main/java/vn/edu/ptit/web_grading_system/executor_service/entity/GradingStepResult.java`
- Modify: `executor-service/src/main/java/vn/edu/ptit/web_grading_system/executor_service/entity/GradingJob.java`
- Modify: `executor-service/src/main/java/vn/edu/ptit/web_grading_system/executor_service/client/ResultServiceClient.java`
- Modify: `executor-service/src/main/java/vn/edu/ptit/web_grading_system/executor_service/service/grading/GradingOrchestrator.java`

**Interfaces:**
- Consumes: Migrations from Task 1.
- Produces: `dbQueryCount`, `dbQueryLogs`, `stackTrace`, `peakMemoryMb` populated through Feign from executor to result-service and returned to API callers.

- [ ] **Step 1: Update `StepResult.java` in `result-service`**

Add fields:
```java
    @Column(name = "db_query_count")
    private Integer dbQueryCount;

    @Column(name = "db_query_logs", columnDefinition = "text")
    private String dbQueryLogs;

    @Column(name = "stack_trace", columnDefinition = "text")
    private String stackTrace;

    @Column(name = "peak_memory_mb")
    private Integer peakMemoryMb;
```

- [ ] **Step 2: Update `StepResultResponse.java` and `CreateResultRequest.java` in `result-service`**

In `StepResultResponse.java`:
```java
    private Integer dbQueryCount;
    private String dbQueryLogs;
    private String stackTrace;
    private Integer peakMemoryMb;
```

In `CreateResultRequest.StepResultItem`:
```java
    private Integer dbQueryCount;
    private String dbQueryLogs;
    private String stackTrace;
    private Integer peakMemoryMb;
```

- [ ] **Step 3: Update `ResultService.java` mapping**

In `toStepResponse(StepResult step)`:
```java
        return StepResultResponse.builder()
                // existing fields...
                .dbQueryCount(step.getDbQueryCount())
                .dbQueryLogs(step.getDbQueryLogs())
                .stackTrace(step.getStackTrace())
                .peakMemoryMb(step.getPeakMemoryMb())
                .build();
```

In `createResult(CreateResultRequest request)` inside step persist loop:
```java
                .dbQueryCount(item.getDbQueryCount())
                .dbQueryLogs(item.getDbQueryLogs())
                .stackTrace(item.getStackTrace())
                .peakMemoryMb(item.getPeakMemoryMb())
```

- [ ] **Step 4: Update `GradingStepResult.java` and `GradingJob.java` in `executor-service`**

In `GradingStepResult.java`:
```java
    @Column(name = "db_query_count")
    private Integer dbQueryCount;

    @Column(name = "db_query_logs", columnDefinition = "text")
    private String dbQueryLogs;

    @Column(name = "stack_trace", columnDefinition = "text")
    private String stackTrace;
```

In `GradingJob.java`:
```java
    @Column(name = "peak_memory_mb")
    private Integer peakMemoryMb;
```

- [ ] **Step 5: Update `ResultServiceClient.java` and `GradingOrchestrator.java` in `executor-service`**

In `ResultServiceClient.StepResultItem`:
```java
    private Integer dbQueryCount;
    private String dbQueryLogs;
    private String stackTrace;
    private Integer peakMemoryMb;
```

In `GradingOrchestrator.stepItem(...)`:
```java
    .dbQueryCount(result == null ? null : result.getDbQueryCount())
    .dbQueryLogs(result == null ? null : truncateForStudent(result.getDbQueryLogs()))
    .stackTrace(result == null ? null : truncateForStudent(result.getStackTrace()))
    .peakMemoryMb(job.getPeakMemoryMb())
```

- [ ] **Step 6: Build & Test result-service and executor-service**

Run:
```bash
mvn -f result-service/pom.xml test-compile
mvn -f executor-service/pom.xml test-compile
```
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add result-service executor-service
git commit -m "feat(telemetry): extend StepResult and GradingStepResult with query and memory telemetry"
```

---

### Task 3: Telemetry Collection in `executor-service` (DB Logging & Stack Trace)

**Files:**
- Modify: `executor-service/src/main/java/vn/edu/ptit/web_grading_system/executor_service/service/step/impl/HttpStepExecutor.java`
- Test: `executor-service/src/test/java/vn/edu/ptit/web_grading_system/executor_service/service/step/impl/HttpStepExecutorTelemetryTest.java`

**Interfaces:**
- Consumes: Docker container logs, HTTP responses.
- Produces: Telemetry fields on `GradingStepResult`.

- [ ] **Step 1: Write test for HttpStepExecutor telemetry extraction**

File: `executor-service/src/test/java/vn/edu/ptit/web_grading_system/executor_service/service/step/impl/HttpStepExecutorTelemetryTest.java`
```java
package vn.edu.ptit.web_grading_system.executor_service.service.step.impl;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

public class HttpStepExecutorTelemetryTest {

    @Test
    void extractSqlQueries_filtersOnlyValidSqlStatements() {
        String logContent = """
            2026-10-15 00:01:00 UTC LOG: statement: SELECT * FROM books WHERE id = 1;
            2026-10-15 00:01:01 UTC LOG: statement: SELECT * FROM authors WHERE book_id = 1;
            2026-10-15 00:01:02 UTC LOG: checkpoint starting: time
        """;
        List<String> queries = HttpStepExecutor.extractSqlQueries(logContent);
        assertThat(queries).hasSize(2);
        assertThat(queries.get(0)).contains("SELECT * FROM books");
    }

    @Test
    void extractStackTrace_capturesJavaStackTraceOnServerError() {
        String errorBody = """
            {"timestamp":"2026-10-15T00:00:00Z","status":500,"error":"Internal Server Error","trace":"java.lang.NullPointerException: Cannot invoke method\\n\\tat com.example.demo.service.UserService.getUser(UserService.java:42)"}
        """;
        String trace = HttpStepExecutor.extractStackTrace(errorBody);
        assertThat(trace).contains("UserService.java:42");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -f executor-service/pom.xml test -Dtest=HttpStepExecutorTelemetryTest`
Expected: Compilation failure / method not found.

- [ ] **Step 3: Implement `extractSqlQueries` and `extractStackTrace` in `HttpStepExecutor.java`**

In `HttpStepExecutor.java`:
```java
    public static List<String> extractSqlQueries(String logContent) {
        if (logContent == null || logContent.isBlank()) {
            return List.of();
        }
        List<String> list = new ArrayList<>();
        Pattern pattern = Pattern.compile("(?i)(statement:\\s*|Hibernate:\\s*)(SELECT|INSERT|UPDATE|DELETE)\\b.*", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(logContent);
        while (matcher.find()) {
            list.add(matcher.group().trim());
        }
        return list;
    }

    public static String extractStackTrace(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        Pattern pattern = Pattern.compile("(?s)(?:at\\s+[\\w\\.$_]+\\(.*?:\\d+\\)|Exception:.*)");
        Matcher matcher = pattern.matcher(responseBody);
        if (matcher.find()) {
            return responseBody.length() <= 10000 ? responseBody : responseBody.substring(0, 10000);
        }
        return null;
    }
```

In `execute(StepContext ctx)`:
When `attemptStatus >= 500`:
```java
String stackTrace = extractStackTrace(responseBody);
```
Populate `dbQueryCount`, `dbQueryLogs`, and `stackTrace` onto `GradingStepResult`.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -f executor-service/pom.xml test -Dtest=HttpStepExecutorTelemetryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add executor-service
git commit -m "feat(executor): implement SQL query and stack trace extraction for HTTP steps"
```

---

### Task 4: Scaffolding `ai-assistant-service`

**Files:**
- Create: `ai-assistant-service/pom.xml`
- Create: `ai-assistant-service/src/main/resources/application.yaml`
- Create: `ai-assistant-service/src/main/resources/application-local.yaml`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/AiAssistantServiceApplication.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/config/SecurityConfig.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/security/GatewayHeaderFilter.java`

**Interfaces:**
- Consumes: HTTP requests forwarded by API Gateway with `X-User-Id`, `X-User-Roles`, `X-Gateway-Secret`.
- Produces: Running Spring Boot microservice on port 8086 with security filter.

- [ ] **Step 1: Create `ai-assistant-service/pom.xml`**

Configure `ai-assistant-service/pom.xml` with parent `spring-boot-starter-parent:4.1.0`, dependencies: `spring-boot-starter-web`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `flyway-database-postgresql`, `postgresql`, `spring-cloud-starter-openfeign`, `gson`, `lombok`, `spring-boot-starter-test`.

- [ ] **Step 2: Create `application.yaml` and `application-local.yaml`**

File: `ai-assistant-service/src/main/resources/application.yaml`
```yaml
server:
  port: 8086

spring:
  application:
    name: ai-assistant-service
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/ai_db}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        default_schema: public
  flyway:
    enabled: true
    baseline-on-migrate: true

gateway:
  security:
    secret: ${GATEWAY_TRUSTED_SECRET:}

feign:
  result-service:
    url: ${RESULT_SERVICE_URL:http://localhost:8084}
  course-service:
    url: ${COURSE_SERVICE_URL:http://localhost:8081}

ai:
  model:
    provider: ${AI_PROVIDER:openai}
    api-key: ${AI_API_KEY:}
    model-name: ${AI_MODEL_NAME:gpt-4o-mini}
    base-url: ${AI_BASE_URL:https://api.openai.com/v1}
```

File: `ai-assistant-service/src/main/resources/application-local.yaml`
```yaml
spring:
  config:
    activate:
      on-profile: local
```

- [ ] **Step 3: Create `AiAssistantServiceApplication.java`**

File: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/AiAssistantServiceApplication.java`
```java
package vn.edu.ptit.web_grading_system.ai_assistant_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class AiAssistantServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiAssistantServiceApplication.class, args);
    }
}
```

- [ ] **Step 4: Create `GatewayHeaderFilter.java` and `SecurityConfig.java`**

Implement `GatewayHeaderFilter` verifying `X-Gateway-Secret` and enriching `SecurityContext` with `X-User-Id` and `X-User-Roles`.
Configure `SecurityConfig` to authenticate requests while securing internal endpoints.

- [ ] **Step 5: Verify build**

Run: `mvn -f ai-assistant-service/pom.xml compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add ai-assistant-service
git commit -m "feat(ai): scaffold ai-assistant-service module with security and configuration"
```

---

### Task 5: Domain Entity, Repository & Feign Clients for `ai-assistant-service`

**Files:**
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/entity/AiExplanation.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/repository/AiExplanationRepository.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/AiExplanationResponse.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/StepAnalysis.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/dto/TelemetryAudit.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/client/ResultServiceClient.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/client/CourseServiceClient.java`

**Interfaces:**
- Consumes: `GET /api/v1/results/{submissionId}` from `result-service`, `GET /api/v1/internal/assignments/{assignmentId}` from `course-service`.
- Produces: Data access layer and Feign interfaces for fetching grading telemetry and assignment specs.

- [ ] **Step 1: Create `AiExplanation.java` entity**

Map table `ai_explanations` with fields: `id`, `submissionId`, `studentId`, `category`, `diagnosis`, `suspectedLocation`, `actionableHints`, `learningKeywords`, `telemetryAudit`, `overallSummary`, `modelName`, `promptTokens`, `completionTokens`, `feedbackRating`, `createdAt`.

- [ ] **Step 2: Create `AiExplanationRepository.java`**

```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.ptit.web_grading_system.ai_assistant_service.entity.AiExplanation;
import java.util.Optional;
import java.util.UUID;

public interface AiExplanationRepository extends JpaRepository<AiExplanation, UUID> {
    Optional<AiExplanation> findBySubmissionIdAndCategory(UUID submissionId, String category);
}
```

- [ ] **Step 3: Create DTOs: `AiExplanationResponse.java`, `StepAnalysis.java`, `TelemetryAudit.java`**

Define structured response DTOs matching the approved design spec:
- `AiExplanationResponse`: `id`, `submissionId`, `category`, `fromCache`, `overallSummary`, `stepAnalyses`, `createdAt`.
- `StepAnalysis`: `stepId`, `stepName`, `stepType`, `passed`, `category`, `diagnosis`, `suspectedLocation`, `actionableHints`, `learningKeywords`, `telemetryAudit`.
- `TelemetryAudit`: `durationMs`, `expectedThresholdMs`, `nPlusOneDetected`, `queryCount`, `sensitiveFieldsExposed`, `peakMemoryMb`.

- [ ] **Step 4: Create Feign clients**

`ResultServiceClient.java` to fetch results from `result-service`.
`CourseServiceClient.java` to fetch assignment metadata from `course-service`.

- [ ] **Step 5: Verify compile**

Run: `mvn -f ai-assistant-service/pom.xml compile`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add ai-assistant-service
git commit -m "feat(ai): add entity, repository, DTOs, and Feign clients"
```

---

### Task 6: Prompt Engineering, Data Sanitizer & AI Orchestration Service

**Files:**
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/DataSanitizer.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/PromptBuilder.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/LlmClient.java`
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/AiExplanationService.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/DataSanitizerTest.java`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/service/AiExplanationServiceTest.java`

**Interfaces:**
- Consumes: Result & assignment data from Feign clients, LLM completion API.
- Produces: `AiExplanationResponse` saved to database cache and returned to user.

- [ ] **Step 1: Write tests for DataSanitizer and AiExplanationService cache logic**

Verify:
- Hidden test payloads and sensitive tokens are masked.
- If analysis exists in DB, it returns cached record with `fromCache == true` without calling LLM.
- If caller is not owner of submission, throws 403 Forbidden.

- [ ] **Step 2: Implement `DataSanitizer.java`**

Mask sensitive headers (Authorization, X-Gateway-Secret, passwords) and mask request/response values marked hidden.

- [ ] **Step 3: Implement `PromptBuilder.java`**

Construct System Prompt and User Prompt:
- System Prompt: Enforce role as pedagogical assistant, strict JSON output schema, no code spoon-feeding, analyze 4 pillars (Fail root-cause, Pass N+1 / Over-fetching / RESTful / Latency).
- User Prompt: Inject assignment title, description, steps summary, actual values, SQL query counts, duration, and memory.

- [ ] **Step 4: Implement `LlmClient.java` and `AiExplanationService.java`**

In `AiExplanationService`:
1. Check cache: `repo.findBySubmissionIdAndCategory(submissionId, "FULL_ANALYSIS")`. If found, map to `AiExplanationResponse` with `fromCache: true` and return immediately.
2. Fetch submission results via `resultServiceClient.getBySubmissionId(...)`.
3. Check student ownership: ensure `callerStudentId.equals(results.get(0).getStudentId())`.
4. Fetch assignment metadata via `courseServiceClient.getAssignment(...)`.
5. Call `PromptBuilder` to format prompt.
6. Call `LlmClient` to get structured JSON analysis.
7. Save analysis into `AiExplanation` entity in `ai_db`.
8. Return `AiExplanationResponse`.

- [ ] **Step 5: Run tests to verify**

Run: `mvn -f ai-assistant-service/pom.xml test`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add ai-assistant-service
git commit -m "feat(ai): implement prompt builder, sanitizer, and ai explanation service with caching"
```

---

### Task 7: REST Controller & API Gateway Route Configuration

**Files:**
- Create: `ai-assistant-service/src/main/java/vn/edu/ptit/web_grading_system/ai_assistant_service/controller/AiExplanationController.java`
- Modify: `api-gateway/src/main/resources/application.yaml`
- Test: `ai-assistant-service/src/test/java/vn/edu/ptit/web_grading_system/ai_assistant_service/controller/AiExplanationControllerTest.java`

**Interfaces:**
- Consumes: HTTP requests via Gateway.
- Produces: `GET /api/v1/ai/submissions/{submissionId}/explain` and `POST /api/v1/ai/submissions/{submissionId}/explain`.

- [ ] **Step 1: Write WebMvc test for `AiExplanationController`**

Verify that:
- `GET` returns 200 with response if cached, 404 if not cached.
- `POST` triggers analysis and returns 200.

- [ ] **Step 2: Implement `AiExplanationController.java`**

```java
package vn.edu.ptit.web_grading_system.ai_assistant_service.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import vn.edu.ptit.web_grading_system.ai_assistant_service.dto.AiExplanationResponse;
import vn.edu.ptit.web_grading_system.ai_assistant_service.service.AiExplanationService;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
public class AiExplanationController {

    private final AiExplanationService explanationService;

    @GetMapping("/submissions/{submissionId}/explain")
    public ResponseEntity<AiExplanationResponse> getExplanation(
            @PathVariable("submissionId") UUID submissionId,
            @RequestHeader("X-User-Id") UUID userId) {
        return explanationService.getExplanation(submissionId, userId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/submissions/{submissionId}/explain")
    public ResponseEntity<AiExplanationResponse> explainSubmission(
            @PathVariable("submissionId") UUID submissionId,
            @RequestHeader("X-User-Id") UUID userId) {
        return ResponseEntity.ok(explanationService.explainSubmission(submissionId, userId));
    }
}
```

- [ ] **Step 3: Update `api-gateway/src/main/resources/application.yaml`**

Add route for `ai-assistant-service`:
```yaml
            - id: ai-assistant-service
              uri: ${AI_ASSISTANT_SERVICE_URI:http://localhost:8086}
              predicates:
                - Path=/api/v1/ai/**
```

- [ ] **Step 4: Run controller test to verify**

Run: `mvn -f ai-assistant-service/pom.xml test -Dtest=AiExplanationControllerTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add ai-assistant-service api-gateway
git commit -m "feat(gateway): route /api/v1/ai/** to ai-assistant-service and expose explain endpoints"
```

---

### Task 8: Frontend TypeScript Types, API Endpoint & React Query Hook

**Files:**
- Create: `web-grading-system-fe/web-grading-system-fe/src/shared/types/ai.ts`
- Create: `web-grading-system-fe/web-grading-system-fe/src/shared/api/endpoints/ai.ts`
- Create: `web-grading-system-fe/web-grading-system-fe/src/features/student/results/useAiExplanation.ts`

**Interfaces:**
- Consumes: `/api/v1/ai/submissions/{submissionId}/explain` endpoints.
- Produces: `useAiExplanation(submissionId)` hook providing `aiData`, `isLoading`, `isAnalyzing`, `hasAnalysis`, and `triggerAnalysis()`.

- [ ] **Step 1: Create `src/shared/types/ai.ts`**

Define `TelemetryAudit`, `StepAnalysis`, `AiExplanationResponse`.

- [ ] **Step 2: Create `src/shared/api/endpoints/ai.ts`**

Implement `getAiExplanation(submissionId)` and `triggerAiExplanation(submissionId)`.

- [ ] **Step 3: Create `src/features/student/results/useAiExplanation.ts`**

Implement React Query integration using `useQuery` for cache check and `useMutation` for analysis trigger.

- [ ] **Step 4: Verify TypeScript compilation**

Run in `web-grading-system-fe/web-grading-system-fe`:
`npm run build` or `npx tsc --noEmit`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add web-grading-system-fe/web-grading-system-fe/src/shared/types/ai.ts web-grading-system-fe/web-grading-system-fe/src/shared/api/endpoints/ai.ts web-grading-system-fe/web-grading-system-fe/src/features/student/results/useAiExplanation.ts
git commit -m "feat(fe): add AI API client, types, and useAiExplanation hook"
```

---

### Task 9: Frontend UI — `AiAnalysisPanel`, Page Integration & i18n

**Files:**
- Create: `web-grading-system-fe/web-grading-system-fe/src/features/student/results/AiAnalysisPanel.tsx`
- Modify: `web-grading-system-fe/web-grading-system-fe/src/features/student/results/SubmissionResultPage.tsx`
- Modify: `web-grading-system-fe/web-grading-system-fe/src/locales/vi.json`
- Modify: `web-grading-system-fe/web-grading-system-fe/src/locales/en.json`

**Interfaces:**
- Consumes: `AiExplanationResponse` from `useAiExplanation`.
- Produces: Visually engaging AI diagnosis panel with 4 pillars, single-click action button with persistent disabled state.

- [ ] **Step 1: Update i18n files (`vi.json` and `en.json`)**

Add keys under `ai` namespace for buttons, diagnosis, hints, keywords, and telemetry warnings.

- [ ] **Step 2: Create `AiAnalysisPanel.tsx`**

Component structure:
- Card with gradient header / AI Icon.
- Tag displaying cached status.
- Overall summary in an `Alert`.
- Step analyses list:
  - Header: Step name, Pass/Fail tag, Category badge (Debug vs Optimize).
  - 🔍 **Diagnosis**: text description.
  - 📍 **Suspected Location**: code file & line reference (if FAIL).
  - 💡 **Actionable Hints**: ordered numbered list of pedagogical guidance.
  - 📚 **Learning Keywords**: Ant Design `Tag` elements.
  - 📊 **Telemetry Audit**: metrics grid (duration, query count, memory, N+1 warning tag).

- [ ] **Step 3: Modify `SubmissionResultPage.tsx`**

1. Import `useAiExplanation` and `AiAnalysisPanel`.
2. Add button in the top action bar:
```tsx
<Button
  type="primary"
  icon={<BulbOutlined />}
  loading={isAnalyzing}
  disabled={hasAnalysis || isAnalyzing}
  onClick={() => triggerAnalysis()}
  style={
    hasAnalysis
      ? undefined
      : {
          background: 'linear-gradient(135deg, #6366F1, #8B5CF6)',
          borderColor: 'transparent',
          fontWeight: 600,
        }
  }
>
  {hasAnalysis ? t('ai.alreadyAnalyzed') : t('ai.analyzeSubmission')}
</Button>
```
3. Below the results table, render:
```tsx
{aiData && <AiAnalysisPanel data={aiData} />}
```

- [ ] **Step 4: Verify frontend build**

Run in `web-grading-system-fe/web-grading-system-fe`:
`npm run build`
Expected: Build successfully without TypeScript or bundle errors.

- [ ] **Step 5: Commit**

```bash
git add web-grading-system-fe
git commit -m "feat(fe): add AiAnalysisPanel, integrate AI button in SubmissionResultPage, and update i18n"
```

---

### Task 10: End-to-End System Smoke Test & Verification

**Files:**
- All touched backend and frontend files.

**Interfaces:**
- End-to-end integration: User clicks AI button on SubmissionResultPage -> Gateway -> ai-assistant-service -> result-service & course-service -> prompt -> cache -> FE display.

- [ ] **Step 1: Verify all Maven services compile cleanly**

Run:
```bash
mvn -f api-gateway/pom.xml clean compile -DskipTests
mvn -f course-service/pom.xml clean compile -DskipTests
mvn -f result-service/pom.xml clean compile -DskipTests
mvn -f executor-service/pom.xml clean compile -DskipTests
mvn -f ai-assistant-service/pom.xml clean compile -DskipTests
```
Expected: All BUILD SUCCESS.

- [ ] **Step 2: Verify frontend builds cleanly**

Run:
```bash
cd web-grading-system-fe/web-grading-system-fe
npm run build
```
Expected: `vite build` completed with 0 errors.

- [ ] **Step 3: Verify single-click & cache idempotency**

Verify that:
1. When user clicks "AI Phân tích bài nộp", `POST /api/v1/ai/submissions/{id}/explain` stores the result into `ai_explanations`.
2. The button transitions to disabled state.
3. Refreshing the browser triggers `GET /api/v1/ai/submissions/{id}/explain` which immediately loads the cached result and keeps the button disabled.
4. No repeated LLM requests can be triggered for the same submission.

- [ ] **Step 4: Final commit and plan completion log**

```bash
git commit --allow-empty -m "chore(ai): complete AI student integration implementation"
```

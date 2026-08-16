package vn.edu.ptit.web_grading_system.executor_service.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "grading_step_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class GradingStepResult extends BaseEntity {

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "step_id", nullable = false)
    private UUID stepId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    @Column(name = "step_type", nullable = false, length = 50)
    private String stepType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StepResultStatus status;

    @Column(name = "actual_status_code")
    private Integer actualStatusCode;

    @Column(name = "request_url")
    private String requestUrl;

    @Column(name = "request_headers", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String requestHeaders;

    @Column(name = "request_body")
    private String requestBody;

    @Column(name = "response_status_code")
    private Integer responseStatusCode;

    @Column(name = "response_headers", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String responseHeaders;

    @Column(name = "response_body")
    private String responseBody;

    @Column(name = "expected_status_code")
    private Integer expectedStatusCode;

    @Column(name = "expected_response_body")
    private String expectedResponseBody;

    @Column(name = "extracted_variables", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String extractedVariables;

    @Column(name = "assertion_result", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String assertionResult;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
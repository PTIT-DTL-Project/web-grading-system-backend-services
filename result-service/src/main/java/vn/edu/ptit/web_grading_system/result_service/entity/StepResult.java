package vn.edu.ptit.web_grading_system.result_service.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

@SQLRestriction("deleted_at IS NULL")
@Entity
@Table(name = "step_results")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class StepResult extends BaseEntity {

    @Column(name = "result_id", nullable = false)
    private UUID resultId;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "step_id")
    private UUID stepId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(name = "step_name", nullable = false)
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 50)
    private StepType stepType;

    @Column(nullable = false)
    private Boolean passed;

    @Column(nullable = false)
    @Builder.Default
    private Boolean skipped = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer weight = 1;

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal score = BigDecimal.ZERO;

    @Column(name = "actual_value", columnDefinition = "text")
    private String actualValue;

    @Column(name = "expected_value", columnDefinition = "text")
    private String expectedValue;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Integer durationMs;

    // Review: 2026-10-11, phase A2 verbose evidence (V7; NULL = not captured).
    @Column(name = "assertion_details", columnDefinition = "text")
    private String assertionDetails;

    @Column(name = "extracted_vars", columnDefinition = "text")
    private String extractedVars;

    @Column(name = "request_url", columnDefinition = "text")
    private String requestUrl;

    @Column(name = "request_body", columnDefinition = "text")
    private String requestBody;

    @Column(name = "response_status")
    private Integer responseStatus;
}
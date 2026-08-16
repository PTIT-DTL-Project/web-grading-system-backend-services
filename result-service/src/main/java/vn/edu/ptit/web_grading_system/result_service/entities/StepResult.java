package vn.edu.ptit.web_grading_system.result_service.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

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
    private Integer weight = 1;

    @Column(nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal score = BigDecimal.ZERO;

    @Column(name = "actual_value", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String actualValue;

    @Column(name = "expected_value", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String expectedValue;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "duration_ms")
    private Integer durationMs;
}
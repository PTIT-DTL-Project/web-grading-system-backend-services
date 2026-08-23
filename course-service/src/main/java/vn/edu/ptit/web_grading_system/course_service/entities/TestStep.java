package vn.edu.ptit.web_grading_system.course_service.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@SQLRestriction("deleted_at IS NULL")
@Entity
@Table(name = "test_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class TestStep extends BaseEntity {

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "step_order", nullable = false)
    private Integer stepOrder;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "step_type", nullable = false, length = 50)
    private StepType stepType;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    @Builder.Default
    private String config = "{}";

    @Column(name = "expected_result", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String expectedResult;

    @Column(nullable = false)
    @Builder.Default
    private Integer weight = 1;

    @Column(name = "timeout_ms")
    private Integer timeoutMs;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private Boolean required = true;
}
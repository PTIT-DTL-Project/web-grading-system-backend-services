package vn.edu.ptit.web_grading_system.executor_service.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "grading_saga_steps")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class GradingSagaStep extends BaseEntity
{
    @Column(name = "saga_id", nullable = false)
    private UUID sagaId;

    @Column(name = "step_name", nullable = false, length = 300)
    private String stepName;

    @Column(name = "plan_id")
    private UUID planId;

    @Column(name = "step_id")
    private UUID stepId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SagaStepStatus status = SagaStepStatus.STARTED;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}

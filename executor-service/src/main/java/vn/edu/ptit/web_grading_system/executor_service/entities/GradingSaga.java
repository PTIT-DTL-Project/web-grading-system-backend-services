package vn.edu.ptit.web_grading_system.executor_service.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import vn.edu.ptit.web_grading_system.executor_service.Constant;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "grading_sagas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class GradingSaga extends BaseEntity
{
    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "saga_type", nullable = false, length = 50)
    @Builder.Default
    private String sagaType = Constant.Saga.GRADE_SUBMISSION;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SagaStatus status = SagaStatus.STARTED;

    @Column(name = "current_step", length = 100)
    private String currentStep;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private OffsetDateTime startedAt = OffsetDateTime.now();

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}

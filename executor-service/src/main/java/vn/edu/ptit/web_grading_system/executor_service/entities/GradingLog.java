package vn.edu.ptit.web_grading_system.executor_service.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@SQLRestriction("deleted_at IS NULL")
@Entity
@Table(name = "grading_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class GradingLog extends BaseEntity {

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    private String step;

    @Column(nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private GradingLogLevel level = GradingLogLevel.INFO;
}
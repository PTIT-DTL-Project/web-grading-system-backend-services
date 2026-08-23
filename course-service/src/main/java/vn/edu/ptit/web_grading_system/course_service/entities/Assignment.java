package vn.edu.ptit.web_grading_system.course_service.entities;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

@SQLRestriction("deleted_at IS NULL")
@Entity
@Table(name = "assignments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Assignment extends BaseEntity {

    @Column(name = "class_id", nullable = false)
    private UUID classId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String title;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "grading_strategy", nullable = false, length = 30)
    @Builder.Default
    private GradingStrategy gradingStrategy = GradingStrategy.STUDENT_DOCKER_COMPOSE;

    @Column(name = "docker_compose_template")
    private String dockerComposeTemplate;

    @Column(name = "docker_compose_port", nullable = false)
    @Builder.Default
    private Integer dockerComposePort = 8080;

    @Column(name = "startup_timeout_ms", nullable = false)
    @Builder.Default
    private Integer startupTimeoutMs = 60000;

    @Column(name = "execution_timeout_ms", nullable = false)
    @Builder.Default
    private Integer executionTimeoutMs = 300000;

    @Column(name = "max_memory_mb", nullable = false)
    @Builder.Default
    private Integer maxMemoryMb = 256;

    @Column(name = "max_cpu", nullable = false)
    @Builder.Default
    private Double maxCpu = 0.5;

    @Column(nullable = false)
    @Builder.Default
    private Boolean published = false;
}
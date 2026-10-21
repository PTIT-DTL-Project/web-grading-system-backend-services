package vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_run")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Run {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status", nullable = false, length = 30)
    private RunStatus status = RunStatus.QUEUED;

    @Column(name = "parent_run_id")
    private UUID parentRunId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "checkpoint_json", columnDefinition = "jsonb")
    private String checkpointJson;

    @Builder.Default
    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "limits_json", columnDefinition = "jsonb")
    private String limitsJson;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "usage_json", columnDefinition = "jsonb")
    private String usageJson;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}

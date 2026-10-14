package vn.edu.ptit.web_grading_system.ai_assistant_service.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "ai_explanations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiExplanation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String category = "FULL_ANALYSIS";

    @Column(nullable = false, columnDefinition = "text")
    private String diagnosis;

    @Column(name = "suspected_location", columnDefinition = "text")
    private String suspectedLocation;

    @Column(name = "actionable_hints", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String actionableHints;

    @Column(name = "learning_keywords", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String learningKeywords;

    @Column(name = "telemetry_audit", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String telemetryAudit;

    @Column(name = "overall_summary", columnDefinition = "text")
    private String overallSummary;

    @Column(name = "model_name", length = 50)
    private String modelName;

    @Column(name = "prompt_tokens")
    private Integer promptTokens;

    @Column(name = "completion_tokens")
    private Integer completionTokens;

    @Column(name = "feedback_rating")
    private Short feedbackRating;

    @Column(name = "created_at")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}

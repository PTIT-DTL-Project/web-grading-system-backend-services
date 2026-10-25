package vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_tool_execution")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ToolExecution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Column(name = "tool_call_id", length = 100)
    private String toolCallId;

    @Column(name = "tool_name", nullable = false, length = 100)
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "arguments_redacted", columnDefinition = "jsonb")
    private String argumentsRedacted;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_redacted", columnDefinition = "jsonb")
    private String resultRedacted;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}

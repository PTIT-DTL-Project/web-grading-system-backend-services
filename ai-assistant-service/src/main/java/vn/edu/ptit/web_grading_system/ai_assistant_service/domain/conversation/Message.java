package vn.edu.ptit.web_grading_system.ai_assistant_service.domain.conversation;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_message")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;

    @Column(name = "run_id")
    private UUID runId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "tool_call_id", length = 100)
    private String toolCallId;

    @Column(name = "tool_name", length = 100)
    private String toolName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_arguments", columnDefinition = "jsonb")
    private String toolArguments;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_result", columnDefinition = "jsonb")
    private String toolResult;

    @Column(name = "client_message_id", length = 100)
    private String clientMessageId;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}

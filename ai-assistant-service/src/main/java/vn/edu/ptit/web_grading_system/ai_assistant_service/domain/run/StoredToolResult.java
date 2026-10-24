package vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ai_stored_tool_result")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StoredToolResult {
    @Id
    private UUID id;
    @Column(name = "run_id", nullable = false)
    private UUID runId;
    @Column(name = "conversation_id", nullable = false)
    private UUID conversationId;
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;
    @Column(name = "role_scope", nullable = false, length = 100)
    private String roleScope;
    @Column(name = "full_result", nullable = false, columnDefinition = "text")
    private String fullResult;
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}

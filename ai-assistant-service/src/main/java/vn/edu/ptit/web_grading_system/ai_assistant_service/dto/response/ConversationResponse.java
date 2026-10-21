package vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID ownerId,
        String title,
        UUID activeTaskId,
        Instant createdAt,
        Instant updatedAt
) {}

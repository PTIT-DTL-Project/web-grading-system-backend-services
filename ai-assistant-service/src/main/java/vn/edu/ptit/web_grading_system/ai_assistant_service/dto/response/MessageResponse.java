package vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID conversationId,
        UUID runId,
        String role,
        String content,
        String toolCallId,
        String toolName,
        String toolArguments,
        String toolResult,
        String clientMessageId,
        Integer sequenceNo,
        Instant createdAt
) {}

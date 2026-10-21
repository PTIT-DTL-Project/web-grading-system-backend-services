package vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response;

import java.util.UUID;

public record SendMessageResponse(
        UUID conversationId,
        UUID runId,
        UUID messageId
) {}

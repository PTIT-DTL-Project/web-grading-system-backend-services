package vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response;

import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.run.RunStatus;

import java.time.Instant;
import java.util.UUID;

public record RunResponse(
        UUID id,
        UUID conversationId,
        RunStatus status,
        String errorCode,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
) {}

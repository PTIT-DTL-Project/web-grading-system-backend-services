package vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response;

import vn.edu.ptit.web_grading_system.ai_assistant_service.domain.operation.OperationStatus;

import java.time.Instant;
import java.util.UUID;

public record OperationResponse(
        UUID id,
        UUID taskId,
        String idempotencyKey,
        String commandType,
        OperationStatus status,
        String resultRef,
        Integer attempts,
        Instant nextCheckAt,
        Instant createdAt,
        Instant updatedAt
) {}

package vn.edu.ptit.web_grading_system.ai_assistant_service.dto.response;

import java.time.Instant;
import java.util.UUID;

public record RunEventDto(
        Long id,
        UUID runId,
        Integer sequence,
        String type,
        String payloadJson,
        Instant createdAt
) {}

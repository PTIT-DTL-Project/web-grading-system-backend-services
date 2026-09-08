package vn.edu.ptit.web_grading_system.course_service.dto.internal;

import java.util.UUID;
import lombok.Builder;

/** Raw internal contract for executor-service. No envelope. */
@Builder
public record AssignmentGradingConfigDto(
        UUID id,
        UUID classId,
        String gradingStrategy,
        String dockerComposeTemplate,
        Integer dockerComposePort,
        Integer startupTimeoutMs,
        Integer executionTimeoutMs,
        Integer maxMemoryMb,
        Double maxCpu) {
}